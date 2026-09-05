/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.PropertySource;

import io.meeds.spring.AvailableIntegration;
import io.meeds.spring.kernel.PortalApplicationContextInitializer;

/**
 * Makes the eXo Processes add-on participate in the portal's shared Spring
 * {@code ApplicationContext}, so its Spring-annotated beans (notably the
 * {@code ProcessesMcpTool} {@code @Service}, active under the {@code mcp-server}
 * profile in the processes-services jar) are discovered and collected by the
 * mcp-server tool registry.
 * <p>
 * The add-on's existing persistence (JPA / Liquibase, wired through the kernel
 * {@code configuration.xml}) is untouched: this Spring context lives alongside
 * it and owns no data layer of its own, so the Spring Boot DataSource / JPA /
 * Liquibase auto-configurations (present transitively on the classpath) are
 * fully excluded. Otherwise Spring Boot would try to build an
 * {@code entityManagerFactory} depending on a {@code liquibase} bean whose
 * default {@code classpath:/db/changelog/db.changelog-master.yaml} does not
 * exist here, failing context startup at boot.
 */
@SpringBootApplication(scanBasePackages = {
  ProcessesApplication.MODULE_NAME,
  AvailableIntegration.KERNEL_MODULE,
  AvailableIntegration.WEB_MODULE,
}, exclude = {
  LiquibaseAutoConfiguration.class,
  DataSourceAutoConfiguration.class,
  DataSourceTransactionManagerAutoConfiguration.class,
  HibernateJpaAutoConfiguration.class,
  DataJpaRepositoriesAutoConfiguration.class,
})
@PropertySource("classpath:application.properties")
@PropertySource("classpath:application-common.properties")
public class ProcessesApplication extends PortalApplicationContextInitializer {

  public static final String MODULE_NAME = "org.exoplatform.processes.mcp";

}
