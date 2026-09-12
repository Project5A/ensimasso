package fr.ensim.asso.shared.security;

import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableWebSecurity
public @interface EnableWebSecurityMarker { }
