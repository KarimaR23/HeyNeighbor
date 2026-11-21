package com.hn.heyneighbor.nav;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The navigation each role will have in the finished application.
 * Only the Sprint IV screens are enabled; the rest are visible placeholders.
 * Hiding a link is not security: access is checked by AuthInterceptor.
 */
@Service
public class NavService {

    private static final Map<String, List<NavItem>> NAV = Map.of(

            "administrator", List.of(
                    new NavItem("Manage Users", "/admin/users", true, "👥", "Search, browse and list every user account"),
                    new NavItem("Organization Approvals", "#", false, "✅", "Approve or reject community partner registrations"),
                    new NavItem("Manage Categories", "/admin/categories", true, "🗂️", "Add, rename and deactivate listing categories"),
                    new NavItem("Review Reports", "#", false, "🚩", "Review escalated reports and cases"),
                    new NavItem("Generate Reports", "/admin/reports", true, "📊", "Usage and activity reports"),
                    new NavItem("System Settings", "#", false, "⚙️", "Search radius, photo limits and other settings")
            ),

            "community_user", List.of(
                    new NavItem("Browse Listings", "/community/listings", true, "🔎", "Search free items and services near you"),
                    new NavItem("Post a Listing", "#", false, "➕", "Give away or trade something"),
                    new NavItem("My Requests", "#", false, "📨", "Requests and trade proposals you have made"),
                    new NavItem("My Exchanges", "#", false, "🤝", "Reserved and completed exchanges"),
                    new NavItem("Messages", "#", false, "💬", "Talk with neighbors"),
                    new NavItem("Favorites", "#", false, "⭐", "Listings you saved"),
                    new NavItem("Reviews", "#", false, "📝", "Rate people after an exchange")
            ),

            "community_partner", List.of(
                    new NavItem("Requests for Our Listings", "/partner/requests", true, "📨", "See who has asked for your goods and services"),
                    new NavItem("Our Listings", "#", false, "📦", "Post and manage your organization's resources"),
                    new NavItem("Exchanges", "#", false, "🤝", "Reserved and completed handoffs"),
                    new NavItem("Messages", "#", false, "💬", "Talk with the people you serve"),
                    new NavItem("Organization Profile", "#", false, "🏢", "Hours, services and contact details"),
                    new NavItem("Feedback", "#", false, "⭐", "Reviews of your organization")
            ),

            "moderator", List.of(
                    new NavItem("Reported Listings", "/moderator/reports", true, "🚩", "Review listings that have been reported"),
                    new NavItem("Reported Users", "#", false, "👤", "Review reports about user conduct"),
                    new NavItem("Reported Reviews", "#", false, "⭐", "Moderate reported ratings and comments"),
                    new NavItem("Reported Messages", "#", false, "💬", "Review messages reported by users"),
                    new NavItem("Moderation Cases", "#", false, "📁", "Create, update and close moderation cases"),
                    new NavItem("Warnings and Restrictions", "#", false, "⛔", "Warn or temporarily restrict users"),
                    new NavItem("Escalations", "#", false, "⬆️", "Send issues to a coordinator or administrator")
            ),

            "support_coordinator", List.of(
                    new NavItem("Open Support Requests", "/support/requests", true, "🎧", "Search and browse requests that need help"),
                    new NavItem("Disputes", "#", false, "⚖️", "Exchange disputes and recommended resolutions"),
                    new NavItem("Partner Assistance", "#", false, "🏢", "Help for community partners"),
                    new NavItem("Account Assistance", "#", false, "🔑", "Help users with account and application issues"),
                    new NavItem("Role Change Requests", "#", false, "🔁", "Requests routed to an administrator"),
                    new NavItem("Support Reports", "#", false, "📊", "View recurring support issues and trends"),
                    new NavItem("Escalations", "#", false, "⬆️", "Send a request to a moderator or administrator")
            )

    );

    public List<NavItem> forRole(String roleName) {
        return NAV.getOrDefault(roleName, List.of());
    }
}