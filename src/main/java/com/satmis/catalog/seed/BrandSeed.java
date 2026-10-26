package com.satmis.catalog.seed;

import java.util.List;

record BrandSeed(String name, List<ModelSeed> models) {

    record ModelSeed(String name, List<String> series) {
    }
}
