package dev.noby.documents.dto;

import java.util.List;

public record PackageSnapshot(String name, String reference, String status, List<PackageItemSnapshot> items) {
}
