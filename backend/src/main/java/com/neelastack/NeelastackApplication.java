package com.neelastack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

// VIA_DTO makes Spring wrap any Page<T> returned from a @RestController in a
// PagedModel behind the scenes, which serializes cleanly to plain JSON
// (content/totalElements/totalPages/...) instead of Jackson's raw PageImpl
// representation — the latter isn't guaranteed stable across Spring Data
// versions and is what triggers the "Serializing PageImpl instances as-is
// is not supported" warning. No controller changes needed: they can keep
// returning Page<...> and this wraps it automatically.
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class NeelastackApplication {
    public static void main(String[] args) {
        SpringApplication.run(NeelastackApplication.class, args);
    }
}
