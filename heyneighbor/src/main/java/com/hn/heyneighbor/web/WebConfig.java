package com.hn.heyneighbor.web;

import com.hn.heyneighbor.security.AuthInterceptor;
import com.hn.heyneighbor.service.ListingPhotoStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ListingPhotoStorage photos;

    public WebConfig(AuthInterceptor authInterceptor, ListingPhotoStorage photos) {
        this.authInterceptor = authInterceptor;
        this.photos = photos;
    }

    /** Every page needs a signed-in user except the public home page, sign-in, sign-up and static files. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/login", "/signup", "/css/**", "/images/**", "/js/**",
                        "/error", "/error/**", "/favicon.ico", "/uploads/listings/**");
    }

    /** Listing photos saved by Post a Listing (ListingPhotoStorage), served like other images. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Spring only treats a location as a folder when it ends with "/" (it does not until the folder exists)
        String folder = photos.folder().toUri().toString();
        registry.addResourceHandler("/uploads/listings/**")
                .addResourceLocations(folder.endsWith("/") ? folder : folder + "/");
    }

    /** Passwords are stored and compared as bcrypt hashes only (BR-04). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}