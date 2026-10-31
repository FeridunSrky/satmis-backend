package com.satmis.report;

import com.satmis.report.dto.AdminListingReportResponse;
import com.satmis.report.dto.ListingReportResponse;
import com.satmis.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReportMapper {

    @Mapping(target = "listingId", source = "listing.id")
    ListingReportResponse toResponse(ListingReport report);

    AdminListingReportResponse toAdminResponse(ListingReport report);

    AdminListingReportResponse.Reporter toReporter(User user);
}
