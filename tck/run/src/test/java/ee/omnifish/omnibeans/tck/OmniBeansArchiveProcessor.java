/*
 * Copyright 2026 OmniFish
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package ee.omnifish.omnibeans.tck;

import java.net.URL;
import java.util.logging.Logger;

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import tck.arquillian.porting.lib.spi.AbstractTestArchiveProcessor;

/**
 * Processes the archives the TCK's tests deploy, which the containers would otherwise get as the TCK creates them
 * (for GlassFish, this adds GlassFish's deployment descriptors). It doesn't change them yet.
 */
public class OmniBeansArchiveProcessor extends AbstractTestArchiveProcessor {

    private static final Logger logger = Logger.getLogger(OmniBeansArchiveProcessor.class.getName());

    @Override
    public void initalize(@Observes ArquillianDescriptor descriptor) {
        // Sets up the resource provider
        super.initalize(descriptor);
    }

    @Override
    public void processClientArchive(JavaArchive clientArchive, Class<?> testClass, URL sunXmlUrl) {
        logger.fine(() -> "Client archive " + clientArchive.getName() + " of " + testClass.getName());
    }

    @Override
    public void processEjbArchive(JavaArchive ejbArchive, Class<?> testClass, URL sunXmlUrl) {
        logger.fine(() -> "EJB archive " + ejbArchive.getName() + " of " + testClass.getName());
    }

    @Override
    public void processWebArchive(WebArchive webArchive, Class<?> testClass, URL sunXmlUrl) {
        logger.fine(() -> "Web archive " + webArchive.getName() + " of " + testClass.getName());
    }

    @Override
    public void processRarArchive(JavaArchive rarArchive, Class<?> testClass, URL sunXmlUrl) {
        logger.fine(() -> "Resource adapter archive " + rarArchive.getName() + " of " + testClass.getName());
    }

    @Override
    public void processParArchive(JavaArchive parArchive, Class<?> testClass, URL sunXmlUrl) {
        logger.fine(() -> "Persistence archive " + parArchive.getName() + " of " + testClass.getName());
    }

    @Override
    public void processEarArchive(EnterpriseArchive earArchive, Class<?> testClass, URL sunXmlUrl) {
        logger.fine(() -> "Enterprise archive " + earArchive.getName() + " of " + testClass.getName());
    }
}
