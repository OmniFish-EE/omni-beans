/*
 * Copyright 2022 OmniFish
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
 package org.omnifaces.omnibeans.test;

import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.jboss.shrinkwrap.api.asset.EmptyAsset.INSTANCE;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;

 @ExtendWith(ArquillianExtension.class)
 public class TransactionTest {

     @ArquillianResource
     private URL base;

     WebClient webClient;

     @Deployment(testable = false)
     public static WebArchive createDeployment() {
         try {
         WebArchive webArchive = create(WebArchive.class)
                 .addAsManifestResource(INSTANCE, "beans.xml")
                 .addAsResource("META-INF/persistence.xml")
                 .addAsWebInfResource(new File("src/main/webapp/WEB-INF", "web.xml"))
                 .addAsWebInfResource(new File("src/main/webapp/WEB-INF", "beans.xml"))
                 .addClasses(MyEntity.class, MyEntityService.class, PublicServlet1.class, PublicServlet2.class)
//                 .addAsLibraries(Maven.resolver()
//                         .loadPomFromFile("pom.xml")
//                         .resolve(
//                             "org.omnifaces:omnibeans",
//                             "org.omnifaces:omniservices",
//                             "org.omnifaces:omniutils",
//                             "com.h2database:h2")
//                         .withTransitivity()
//                         .asFile())
                         ;

         System.out.println(webArchive.toString(true));

         return webArchive;
         } catch (Exception e) {
             e.printStackTrace();
             throw e;
         }
     }

     @BeforeEach
     public void setup() {
         webClient = new WebClient();
         webClient.getOptions().setTimeout(0);
     }


     @Test
     @RunAsClient
     public void testGet() throws IOException {
         TextPage page = webClient.getPage(base + "servlet1");

         System.out.println("Content: \n" + page.getContent());

         page = webClient.getPage(base + "servlet2");

         System.out.println("Content: \n" + page.getContent());

         assertTrue(page.getContent().contains("my-test"));
     }


 }