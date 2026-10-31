package com.satmis.listing;

import com.satmis.catalog.District;
import com.satmis.catalog.DistrictRepository;
import com.satmis.catalog.VehicleSeries;
import com.satmis.catalog.VehicleSeriesRepository;
import com.satmis.common.ApiException;
import com.satmis.enums.ListingStatus;
import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.listing.dto.ListingRequest;
import com.satmis.vehicle.VehicleDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ListingService {

    // Approval states (PENDING_APPROVAL, REJECTED) are left only through an admin or an edit, never by the owner
    private static final Map<ListingStatus, Set<ListingStatus>> OWNER_TRANSITIONS = Map.of(
            ListingStatus.ACTIVE, EnumSet.of(ListingStatus.PASSIVE, ListingStatus.SOLD),
            ListingStatus.PASSIVE, EnumSet.of(ListingStatus.ACTIVE, ListingStatus.SOLD));

    private final ListingRepository listingRepository;
    private final DistrictRepository districtRepository;
    private final VehicleSeriesRepository vehicleSeriesRepository;
    private final ListingOwnerPolicy listingOwnerPolicy;
    private final ListingMapper listingMapper;
    private final ListingDetailAssembler listingDetailAssembler;
    private final ListingImageService listingImageService;

    @Transactional
    public ListingDetailResponse create(Long ownerId, ListingRequest request) {
        Listing listing = new Listing();
        listing.setOwner(listingOwnerPolicy.requireAllowedToList(ownerId));
        listing.setVehicleDetail(new VehicleDetail());
        applyRequest(listing, request);
        // Images point at the listing, so it needs its id first
        Listing saved = listingRepository.save(listing);
        listingImageService.replaceListingImages(saved, request.imageIds());
        return listingDetailAssembler.assemble(saved, ownerId);
    }

    @Transactional
    public ListingDetailResponse update(Long listingId, Long userId, ListingRequest request) {
        Listing listing = findOwnedListing(listingId, userId);
        if (listing.getStatus() == ListingStatus.SOLD) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Satılmış ilan güncellenemez");
        }
        if (!listing.getVersion().equals(request.version())) {
            throw staleVersion();
        }
        listingRepository.forceVersionIncrement(listing);
        boolean needsReview = listing.getStatus() == ListingStatus.REJECTED
                || moderatedContentChanged(listing, request);
        applyRequest(listing, request);
        listingImageService.replaceListingImages(listing, request.imageIds());
        if (needsReview) {
            listing.submitForApproval();
        }
        // Flush so the response carries the version the client must send next time
        listingRepository.flush();
        return listingDetailAssembler.assemble(listing, userId);
    }

    @Transactional
    public ListingDetailResponse changeStatus(Long listingId, Long userId, ListingStatus targetStatus) {
        Listing listing = findOwnedListing(listingId, userId);
        if (!OWNER_TRANSITIONS.getOrDefault(listing.getStatus(), Set.of()).contains(targetStatus)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Bu durum geçişine izin verilmiyor");
        }
        listing.setStatus(targetStatus);
        listingRepository.flush();
        return listingDetailAssembler.assemble(listing, userId);
    }

    @Transactional
    public void delete(Long listingId, Long userId) {
        Listing listing = findOwnedListing(listingId, userId);
        listingImageService.releaseImages(listing);
        listingRepository.delete(listing);
    }

    private void applyRequest(Listing listing, ListingRequest request) {
        listingMapper.updateListing(request, listing);
        listing.setDistrict(findDistrict(request.districtId()));

        VehicleDetail vehicleDetail = listing.getVehicleDetail();
        listingMapper.updateVehicleDetail(request.vehicle(), vehicleDetail);
        VehicleSeries series = findSeries(request.vehicle().seriesId());
        vehicleDetail.setSeries(series);
        listing.setSearchText(searchTextOf(listing, series));
    }

    // Same layout as the V8 backfill: brand, model, series, title, description
    private static String searchTextOf(Listing listing, VehicleSeries series) {
        return Stream.of(series.getModel().getBrand().getName(), series.getModel().getName(), series.getName(),
                        listing.getTitle(), listing.getDescription())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    // What an admin approved: text, price and the set of images. Other details (km, colour, ...) change freely;
    // reordering images only picks a different approved image as cover.
    private static boolean moderatedContentChanged(Listing listing, ListingRequest request) {
        Set<Long> currentImageIds = listing.getImages().stream().map(ListingImage::getId)
                .collect(Collectors.toSet());
        return !Objects.equals(listing.getTitle(), request.title())
                || !Objects.equals(listing.getDescription(), request.description())
                || listing.getPrice().compareTo(request.price()) != 0
                || listing.getCurrency() != request.currency()
                || !currentImageIds.equals(new HashSet<>(request.imageIds()));
    }

    private Listing findOwnedListing(Long listingId, Long userId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "İlan bulunamadı"));
        if (!listing.getOwner().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bu ilan üzerinde işlem yapma yetkiniz yok");
        }
        return listing;
    }

    private District findDistrict(Long districtId) {
        return districtRepository.findById(districtId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "İlçe bulunamadı"));
    }

    private VehicleSeries findSeries(Long seriesId) {
        return vehicleSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Araç serisi bulunamadı"));
    }

    private static ApiException staleVersion() {
        return new ApiException(HttpStatus.CONFLICT,
                "İlan başka bir işlemle güncellendi, sayfayı yenileyip tekrar deneyin");
    }
}
