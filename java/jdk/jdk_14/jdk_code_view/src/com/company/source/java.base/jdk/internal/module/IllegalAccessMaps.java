/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package jdk.internal.module;

import sun.nio.cs.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generates the maps of concealed and exported packages to open at run-time.
 *
 * This is used at run-time for exploded builds, and at link-time to generate
 * the maps for the system modules in the run-time image.
 */

public class IllegalAccessMaps {
    private final Map<String, Set<String>> concealedPackagesToOpen;
    private final Map<String, Set<String>> exportedPackagesToOpen;

    private IllegalAccessMaps(Map<String, Set<String>> map1,
                              Map<String, Set<String>> map2) {
        this.concealedPackagesToOpen = map1;
        this.exportedPackagesToOpen = map2;
    }

    /**
     * Returns the map of concealed packages to open. The map key is the
     * module name, the value is the set of concealed packages to open.
     */
    public Map<String, Set<String>> concealedPackagesToOpen() {
        return concealedPackagesToOpen;
    }

    /**
     * Returns the map of exported packages to open. The map key is the
     * module name, the value is the set of exported packages to open.
     */
    public Map<String, Set<String>> exportedPackagesToOpen() {
        return exportedPackagesToOpen;
    }

    /**
     * Generate the maps of module to concealed and exported packages for
     * the system modules that are observable with the given module finder.
     */
    public static IllegalAccessMaps generate(ModuleFinder finder) {
        Map<String, ModuleDescriptor> map = new HashMap<>();
        finder.findAll().stream()
            .map(ModuleReference::descriptor)
            .forEach(md -> md.packages().forEach(pn -> map.putIfAbsent(pn, md)));

        Map<String, Set<String>> concealedPackagesToOpen = new HashMap<>();
        Map<String, Set<String>> exportedPackagesToOpen = new HashMap<>();

        String rn = "jdk8_packages.dat";
        InputStream in = IllegalAccessMaps.class.getResourceAsStream(rn);
        if (in == null) {
            throw new InternalError(rn + " not found");
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, UTF_8.INSTANCE)))
        {
            br.lines()
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .forEach(pn -> {
                    ModuleDescriptor descriptor = map.get(pn);
                    if (descriptor != null && !isOpen(descriptor, pn)) {
                        String name = descriptor.name();
                        if (isExported(descriptor, pn)) {
                            exportedPackagesToOpen.computeIfAbsent(name,
                                    k -> new HashSet<>()).add(pn);
                        } else {
                            concealedPackagesToOpen.computeIfAbsent(name,
                                    k -> new HashSet<>()).add(pn);
                        }
                    }
                });

        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }

        return new IllegalAccessMaps(concealedPackagesToOpen, exportedPackagesToOpen);
    }

    private static boolean isExported(ModuleDescriptor descriptor, String pn) {
        return descriptor.exports()
                .stream()
                .anyMatch(e -> e.source().equals(pn) && !e.isQualified());
    }

    private static boolean isOpen(ModuleDescriptor descriptor, String pn) {
        return descriptor.opens()
                .stream()
                .anyMatch(e -> e.source().equals(pn) && !e.isQualified());
    }
}
