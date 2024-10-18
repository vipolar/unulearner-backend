package com.unulearner.backend.security.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class JWTCredentials {

    /**
     * Extract user UUID from the JWT
     * @return UUID as a String
     */
    public String getUser() {
        /* Get authentication from SecurityContextHolder */
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            final Jwt jwt = (Jwt) authentication.getPrincipal();

            /* Extract user ID from JWT */
            final String userUUID = jwt.getClaimAsString("sub"); /* 'sub' is typically used for the user UUID in JWTs */
            if (userUUID != null) {
                return userUUID;
            } else {
                throw new IllegalStateException("No user ID found in the JWT token");
            }
        }

        throw new IllegalStateException("No JWT token found");
    }

    /**
     * Extract UUIDs of the groups user belongs to from the JWT
     * @return UUIDs as a Collection<String>
     */
    public Collection<String> getUserGroups() {
        /* Get authentication from SecurityContextHolder */
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            final Jwt jwt = (Jwt) authentication.getPrincipal();

            /* Extract groups from JWT */
            final Collection<String> groups_uuid = jwt.getClaimAsStringList("groups_uuid");
            if (groups_uuid != null) {
                /* Return groups for internal use */
                return groups_uuid;
            } else {
                throw new IllegalStateException("No groups found for the user");
            }
        }

        throw new IllegalStateException("No JWT token found");
    }

    public boolean hasWritePermission() {
        String user = getUser();
        Collection<String> groups = getUserGroups();

        System.out.println("User UUID: %s".formatted(user));

        int index = 0;
        for (String group : groups) {
            System.out.println("User group(%d) UUID: %s".formatted(index++, group));
        }

        // Check if user belongs to a group that has write permission
        if (groups.contains("plebs") || groups.contains("peasants")) {
            return true;
        }

        return false;
    }
}
