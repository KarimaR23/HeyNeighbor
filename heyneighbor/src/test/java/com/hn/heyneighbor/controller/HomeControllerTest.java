package com.hn.heyneighbor.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeControllerTest {

    @Test
    void homeReturnsIndexViewWithNameAndNavigation() {
        HomeController controller = new HomeController();
        Model model = new ConcurrentModel();

        String view = controller.home(model);

        assertEquals("index", view);
        assertEquals("HeyNeighbor", model.getAttribute("appName"));
        assertTrue(Objects.requireNonNull(model.getAttribute("navItems")).toString().contains("Contact Us"));
        assertTrue(Objects.requireNonNull(model.getAttribute("authItems")).toString().contains("Login"));
    }
}