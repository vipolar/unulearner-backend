package com.unulearner.backend.security.user;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

@Service
public class Credentials {

    /**
     * Extract user UUID from the JWT
     * @return UUID as a UUID
     */
    public UUID getUser() {
        /* Get authentication from SecurityContextHolder */
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            final Jwt jwt = (Jwt) authentication.getPrincipal();

            /* Extract user ID from JWT */
            /* 'sub' is typically used for the user UUID in JWTs */
            final String user = jwt.getClaimAsString("sub");
            if (user != null) {
                try {
                    return UUID.fromString(user);
                } catch (IllegalArgumentException exception) {
                    System.err.println("Error converting %s to UUID: %s".formatted(user, exception.getMessage()));
                    return null; /* TODO: log! */
                }
            } else {
                throw new IllegalStateException("No user ID found in the JWT token");
            }
        }

        throw new IllegalStateException("No JWT token found");
    }

    /**
     * Extract UUIDs of the groups user belongs to from the JWT
     * @return UUIDs as a Collection<UUID>
     */
    public Collection<UUID> getUserGroups() {
        /* Get authentication from SecurityContextHolder */
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            final Jwt jwt = (Jwt) authentication.getPrincipal();

            /* Extract groups from JWT */
            final Collection<String> groups = jwt.getClaimAsStringList("groups_uuid");
            if (groups != null) {
                /* Return groups for internal use */

                return groups.stream()
                    .map(group -> {
                        try {
                            return UUID.fromString(group);
                        } catch (IllegalArgumentException exception) {
                            System.err.println("Error converting %s to UUID: %s".formatted(group, exception.getMessage()));
                            return null; /* TODO: log! */
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            } else {
                throw new IllegalStateException("No groups found for the user");
            }
        }

        throw new IllegalStateException("No JWT token found");
    }
}
