/*
 * Copyright 2026 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.apache.yoko;

import org.apache.yoko.orb.CORBA.ORB;
import org.apache.yoko.orb.CORBA.ORBSingleton;
import org.apache.yoko.osgi.ProviderLocator;
import org.apache.yoko.osgi.ProviderRegistry;
import org.apache.yoko.osgi.locator.LocalFactory;
import org.apache.yoko.osgi.locator.ProviderRegistryImpl;
import org.apache.yoko.osgi.locator.Register;
import org.apache.yoko.osgi.locator.ServiceProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.Hashtable;

/**
 * Bundle activator for {@code io.openliberty.yoko.core}.
 * <p>
 * Registers a {@link ProviderRegistryImpl} as both {@link Register} and
 * {@link ProviderRegistry} OSGi services, installs it into
 * {@link ProviderLocator}, and pre-populates it with the Yoko ORB
 * implementation classes so that {@code ORB.init()} in {@code java.base}
 * can find them across OSGi classloader boundaries.
 * <p>
 * Specifically maps:
 * <ul>
 *   <li>{@code org.omg.CORBA.ORBClass} &rarr; {@code org.apache.yoko.orb.CORBA.ORB}</li>
 *   <li>{@code org.omg.CORBA.ORBSingletonClass} &rarr; {@code org.apache.yoko.orb.CORBA.ORBSingleton}</li>
 * </ul>
 * This replaces the IBM-internal {@code org.apache.yoko.orb.activator.Activator}
 * that existed in the official IBM-built yoko-core jar but is absent from the
 * open-source Yoko repository.
 */
public final class Activator implements BundleActivator {

    /**
     * LocalFactory that loads classes using this bundle's own classloader,
     * making them visible to the OSGi provider registry.
     */
    private static final LocalFactory LOCAL_FACTORY = new LocalFactory() {
        @Override
        public Class<?> forName(String clsName) throws ClassNotFoundException {
            return Class.forName(clsName, false, Activator.class.getClassLoader());
        }

        @Override
        @SuppressWarnings("deprecation")
        public Object newInstance(Class cls) throws InstantiationException, IllegalAccessException {
            return cls.newInstance();
        }
    };

    private ProviderRegistryImpl registry;
    private ServiceRegistration<?> registration;

    @Override
    public void start(BundleContext context) {
        registry = new ProviderRegistryImpl();

        // Register ORB implementation classes so ProviderLocator.loadClass() can find
        // them when called from java.base, which cannot see OSGi bundle classloaders.
        // providers — looked up by fully-qualified class name
        registry.registerProvider(new ServiceProvider(LOCAL_FACTORY, ORB.class.getName(),          ORB.class.getName(),          ServiceProvider.DEFAULT_PRIORITY));
        registry.registerProvider(new ServiceProvider(LOCAL_FACTORY, ORBSingleton.class.getName(), ORBSingleton.class.getName(), ServiceProvider.DEFAULT_PRIORITY));
        // services — looked up by the ORB system-property key
        registry.registerService(new ServiceProvider(LOCAL_FACTORY, "org.omg.CORBA.ORBClass",          ORB.class.getName(),          ServiceProvider.DEFAULT_PRIORITY));
        registry.registerService(new ServiceProvider(LOCAL_FACTORY, "org.omg.CORBA.ORBSingletonClass", ORBSingleton.class.getName(), ServiceProvider.DEFAULT_PRIORITY));

        // Set the static registry BEFORE publishing the OSGi service.
        // If setRegistry() were called after registerService(), another bundle could
        // receive the Register service, call registerProvider(), and the ORB could
        // attempt ProviderLocator.loadClass() — all before ProviderLocator.registry is set.
        ProviderLocator.setRegistry(registry);

        Hashtable<String, Object> props = new Hashtable<>();
        props.put("service.vendor", "IBM");
        registration = context.registerService(
                new String[]{ Register.class.getName(), ProviderRegistry.class.getName() },
                registry,
                props);
    }

    @Override
    public void stop(BundleContext context) {
        ProviderLocator.setRegistry(null);
        registration.unregister();
        registration = null;
        registry = null;
    }
}
