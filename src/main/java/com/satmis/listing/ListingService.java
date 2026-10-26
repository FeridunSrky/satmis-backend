package com.satmis.listing;

import com.satmis.catalog.District;
import com.satmis.catalog.DistrictRepository;
import com.satmis.catalog.VehicleSeries;
import com.satmis.catalog.VehicleSeriesRepository;
import com.satmis.common.ApiException;
import com.satmis.enums.ListingStatus;
import com.satmis.listing.dto.ListingDetailResponse;
import com.satmis.listing.dto.ListingRequest;
import com.satmis.user.UserRepository;
import com.satmis.vehicle.VehicleDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListingService {

    // PENDING_APPROVAL transitions belong to the future admin approval flow, not to owners
    private static final Map<ListingStatus, Set<ListingStatus>> OWNER_TRANSITIONS = Map.of(
            ListingStatus.ACTIVE, EnumSet.of(ListingStatus.PASSIVE, ListingStatus.SOLD),
            ListingStatus.PASSIVE, EnumSet.of(ListingStatus.ACTIVE, ListingStatus.SOLD));

    private final ListingRepository listingRepository;
    private final DistrictRepository districtRepository;
    private final VehicleSeriesRepository vehicleSeriesRepository;
    private final UserRepository userRepository;
    private final ListingMapper listingMapper;
    private final ListingDetailAssembler listingDetailAssembler;

    @Transactional
    public ListingDetailResponse create(Long ownerId, ListingRequest request) {
        Listing listing = new Listing();
        listing.setOwner(userRepository.getReferenceById(ownerId));
        listing.setVehicleDetail(new VehicleDetail());
        applyRequest(listing, request);
        return listingDetailAssembler.assemble(listingRepository.save(listing), ownerId);
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
        applyRequest(listing, request);
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
        listingRepository.delete(findOwnedListing(listingId, userId));
    }

    private void applyRequest(Listing listing, ListingRequest request) {
        listingMapper.updateListing(request, listing);
        listing.setDistrict(findDistrict(request.districtId()));

        VehicleDetail vehicleDetail = listing.getVehicleDetail();
        listingMapper.updateVehicleDetail(request.vehicle(), vehicleDetail);
        vehicleDetail.setSeries(findSeries(request.vehicle().seriesId()));

        replaceImages(listing, request.imageUrls());
    }

    // First image is the cover; list order becomes display order
    private static void replaceImages(Listing listing, List<String> imageUrls) {
        listing.getImages().clear();
        for (int index = 0; index < imageUrls.size(); index++) {
            ListingImage image = new ListingImage();
            image.setUrl(imageUrls.get(index));
            image.setDisplayOrder(index);
            image.setCover(index == 0);
            listing.addImage(image);
        }
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
