package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record AsmValidationConfig(
        Set<String> allowedHelperOwners,
        Set<String> allowedStructOwners
) {

    public AsmValidationConfig {
        allowedHelperOwners = Set.copyOf(Objects.requireNonNull(allowedHelperOwners, "allowedHelperOwners"));
        allowedStructOwners = Set.copyOf(Objects.requireNonNull(allowedStructOwners, "allowedStructOwners"));
    }

    public static AsmValidationConfig defaultConfig() {
        return new AsmValidationConfig(Set.of(), Set.of());
    }

    public AsmValidationConfig withHelperOwner(String ownerInternalName) {
        LinkedHashSet<String> owners = new LinkedHashSet<>(allowedHelperOwners);
        owners.add(Objects.requireNonNull(ownerInternalName, "ownerInternalName"));
        return new AsmValidationConfig(owners, allowedStructOwners);
    }

    public AsmValidationConfig withHelperOwners(Collection<String> ownerInternalNames) {
        LinkedHashSet<String> owners = new LinkedHashSet<>(allowedHelperOwners);
        owners.addAll(Objects.requireNonNull(ownerInternalNames, "ownerInternalNames"));
        return new AsmValidationConfig(owners, allowedStructOwners);
    }

    public AsmValidationConfig withStructOwner(String ownerInternalName) {
        LinkedHashSet<String> owners = new LinkedHashSet<>(allowedStructOwners);
        owners.add(Objects.requireNonNull(ownerInternalName, "ownerInternalName"));
        return new AsmValidationConfig(allowedHelperOwners, owners);
    }

    public AsmValidationConfig withStructOwners(Collection<String> ownerInternalNames) {
        LinkedHashSet<String> owners = new LinkedHashSet<>(allowedStructOwners);
        owners.addAll(Objects.requireNonNull(ownerInternalNames, "ownerInternalNames"));
        return new AsmValidationConfig(allowedHelperOwners, owners);
    }
}
