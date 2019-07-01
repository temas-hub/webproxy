package com.temas.webproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;

/**
 * Created by azhdanov on 28.06.2019.
 */
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

//    @Bean
//    public JettyEmbeddedServletContainerFactory jettyEmbeddedServletContainerFactory() {
//        JettyEmbeddedServletContainerFactory jettyContainer =
//                new JettyEmbeddedServletContainerFactory();
//
//
//        jettyContainer.setPort(9099);
//        return jettyContainer;
//    }
}
