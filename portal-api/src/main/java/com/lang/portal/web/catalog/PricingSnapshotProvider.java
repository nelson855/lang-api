package com.lang.portal.web.catalog;

import com.lang.portal.upstream.newapi.pricing.NewApiPricingEntry;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingVendor;
import java.util.List;

public interface PricingSnapshotProvider {
  Snapshot fetchSnapshot();

  record Snapshot(
      List<NewApiPricingEntry> entries, List<NewApiPricingVendor> vendors, String pricingVersion) {}
}
