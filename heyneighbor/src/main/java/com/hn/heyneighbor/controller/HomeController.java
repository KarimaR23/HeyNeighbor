package com.hn.heyneighbor.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
@Controller
public class HomeController {
    /** Navigation placeholder. Links are not wired up yet. */
    public record NavItem(String label, String href) {}

    public record Category(String label, String emoji) {}

    /** Hard-coded sample listing for the demo screen (no database). */
    public record Listing(String title, String type, String category,
                          String distance, String giver, String emoji, String color) {}

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("appName", "HeyNeighbor");
        model.addAttribute("tagline", "Give freely. Share locally. Strengthen your community.");
        model.addAttribute("description",
                "HeyNeighbor connects you with nearby neighbors to give away or trade "
                        + "food, household goods, services, and more - at no cost.");

        model.addAttribute("navItems", List.of(
                new NavItem("Browse", "#"),
                new NavItem("About", "#"),
                new NavItem("Help", "#"),
                new NavItem("Contact Us", "#")));

        model.addAttribute("authItems", List.of(
                new NavItem("Login", "#"),
                new NavItem("Sign Up", "#")));

        model.addAttribute("categories", List.of(
                new Category("Food", "🥕"),
                new Category("Shelter", "🏠"),
                new Category("Utilities", "💡"),
                new Category("Services", "🛠️"),
                new Category("Household", "🛋️"),
                new Category("Trades", "🔁")));

        model.addAttribute("listings", List.of(
                new Listing("Fresh garden tomatoes", "Free", "Food", "0.4 mi", "Maria", "🍅", "#fed7d7"),
                new Listing("Kids' winter coats (sizes 4-8)", "Free", "Household", "1.1 mi", "Dave", "🧥", "#bee3f8"),
                new Listing("Help moving this Saturday", "Free", "Services", "0.8 mi", "Alex", "📦", "#fefcbf"),
                new Listing("Spare room for 2 nights", "Free", "Shelter", "2.3 mi", "Grace Church", "🛏️", "#e9d8fd"),
                new Listing("Bike for a lawn mower", "Trade", "Trades", "1.6 mi", "Sam", "🚲", "#c6f6d5"),
                new Listing("Canned goods & pantry staples", "Free", "Food", "0.9 mi", "Eastside Pantry", "🥫", "#feebc8"),
                new Listing("Phone charger & cables", "Free", "Utilities", "0.3 mi", "Priya", "🔌", "#b2f5ea"),
                new Listing("Free math tutoring", "Free", "Services", "1.9 mi", "Jordan", "📚", "#fed7e2")));

        return "index";
    }
}
