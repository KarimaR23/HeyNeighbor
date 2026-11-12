package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.AnalyticsRepository;
import com.hn.heyneighbor.repository.AnalyticsRepository.Column;
import com.hn.heyneighbor.repository.AnalyticsRepository.Result;
import com.hn.heyneighbor.repository.CategoryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class AdminReportController {

    private static final List<String> RANGES = List.of("7d", "30d", "90d", "year", "custom");
    private static final List<String> COMPARE = List.of("none", "previous");
    private static final long MAX_DAYS = 366;
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final AnalyticsRepository analytics;
    private final CategoryRepository categories;

    public AdminReportController(AnalyticsRepository analytics, CategoryRepository categories) {
        this.analytics = analytics;
        this.categories = categories;
    }

    @GetMapping("/admin/reports")
    public String page(@RequestParam(name = "type", required = false) String type,
                       @RequestParam(name = "range", required = false) String range,
                       @RequestParam(name = "from", required = false) String fromText,
                       @RequestParam(name = "to", required = false) String toText,
                       @RequestParam(name = "group", required = false) String group,
                       @RequestParam(name = "cat", required = false) List<String> cat,
                       @RequestParam(name = "role", required = false) List<String> role,
                       @RequestParam(name = "compare", required = false) String compare,
                       @RequestParam(name = "generate", required = false) String generate,
                       Model model) {

        String typeValue = Options.oneOf(type, AnalyticsRepository.TYPES);
        if (typeValue == null) typeValue = "activity";
        String rangeValue = Options.oneOf(range, RANGES);
        if (rangeValue == null) rangeValue = "30d";
        String groupValue = Options.oneOf(group, AnalyticsRepository.GROUPS);
        if (groupValue == null) groupValue = "week";
        String compareValue = Options.oneOf(compare, COMPARE);
        if (compareValue == null) compareValue = "none";

        List<Map<String, Object>> allCategories = categories.list("all");
        Set<Integer> validIds = new LinkedHashSet<>();
        for (Map<String, Object> c : allCategories) {
            validIds.add(((Number) c.get("category_id")).intValue());
        }
        List<Integer> categoryIds = new ArrayList<>();
        if (cat != null) {
            for (String c : cat) {
                try {
                    int id = Integer.parseInt(c.trim());
                    if (validIds.contains(id) && !categoryIds.contains(id)) categoryIds.add(id);
                } catch (NumberFormatException ignored) {
                    // ignore anything that is not a category id
                }
            }
        }
        List<String> roles = new ArrayList<>();
        if (role != null) {
            for (String r : role) {
                if (AnalyticsRepository.ROLES.contains(r) && !roles.contains(r)) roles.add(r);
            }
        }

        LocalDate today = LocalDate.now();
        LocalDate from;
        LocalDate to = today;
        String error = null;
        switch (rangeValue) {
            case "7d" -> from = today.minusDays(6);
            case "90d" -> from = today.minusDays(89);
            case "year" -> from = today.withDayOfYear(1);
            case "custom" -> {
                LocalDate parsedFrom = parseDate(fromText);
                LocalDate parsedTo = parseDate(toText);
                if (parsedFrom == null || parsedTo == null) {
                    error = "Choose a valid From and To date.";
                    from = today.minusDays(29);
                } else {
                    from = parsedFrom;
                    to = parsedTo;
                }
            }
            default -> from = today.minusDays(29);
        }
        if (error == null && from.isAfter(to)) {
            error = "The From date must be on or before the To date.";
        }
        if (error == null && ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            error = "Choose a range of one year or less.";
        }

        if (generate != null && error == null) {
            Result result = analytics.run(typeValue, groupValue, from, to, categoryIds, roles);
            Map<String, Long> totals = totals(result);

            Map<String, Long> previousTotals = null;
            String previousLabel = null;
            if (compareValue.equals("previous")) {
                long days = ChronoUnit.DAYS.between(from, to) + 1;
                LocalDate previousTo = from.minusDays(1);
                LocalDate previousFrom = previousTo.minusDays(days - 1);
                previousTotals = totals(analytics.run(typeValue, groupValue, previousFrom, previousTo, categoryIds, roles));
                previousLabel = SHOWN.format(previousFrom) + " to " + SHOWN.format(previousTo);
            }

            model.addAttribute("result", result);
            model.addAttribute("summary", summary(result, totals, previousTotals));
            model.addAttribute("rangeLabel", SHOWN.format(from) + " to " + SHOWN.format(to));
            model.addAttribute("previousLabel", previousLabel);
            model.addAttribute("generated", true);
        }

        model.addAttribute("pageTitle", "Generate Report");
        model.addAttribute("error", error);
        model.addAttribute("types", typeCards());
        model.addAttribute("categories", allCategories);
        model.addAttribute("roleOptions", roleOptions());
        model.addAttribute("type", typeValue);
        model.addAttribute("range", rangeValue);
        model.addAttribute("from", from.toString());
        model.addAttribute("to", to.toString());
        model.addAttribute("group", groupValue);
        model.addAttribute("compare", compareValue);
        model.addAttribute("selectedCategories", new LinkedHashSet<>(categoryIds));
        model.addAttribute("selectedRoles", new LinkedHashSet<>(roles));
        return "AdminReports";
    }

    private static LocalDate parseDate(String text) {
        String value = Options.text(text);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Map<String, Long> totals(Result result) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Column column : result.columns()) {
            if (!column.numeric()) continue;
            long sum = 0;
            for (Map<String, Object> row : result.rows()) {
                Object value = row.get(column.key());
                if (value instanceof Number n) sum += n.longValue();
            }
            totals.put(column.key(), sum);
        }
        return totals;
    }

    private static List<Map<String, Object>> summary(Result result, Map<String, Long> totals, Map<String, Long> previous) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Column column : result.columns()) {
            if (!column.numeric()) continue;
            long now = totals.get(column.key());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", column.label());
            item.put("value", now);
            if (previous != null) {
                long before = previous.get(column.key());
                item.put("previous", before);
                if (before == 0) {
                    item.put("change", now == 0 ? "No change" : "New activity");
                    item.put("trend", now == 0 ? "flat" : "up");
                } else {
                    long percent = Math.round((now - before) * 100.0 / before);
                    item.put("change", (percent > 0 ? "+" : "") + percent + "%");
                    item.put("trend", percent > 0 ? "up" : percent < 0 ? "down" : "flat");
                }
            }
            items.add(item);
        }
        return items;
    }

    private static List<Map<String, Object>> typeCards() {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card("activity", "Platform Activity", "Listings, exchanges, new members", "📈"));
        cards.add(card("growth", "User Growth", "Sign-ups by role over time", "👥"));
        cards.add(card("category", "Category Performance", "Listings and claims by category", "🗂️"));
        cards.add(card("partner", "Partner Impact", "Resources provided by partners", "🏠"));
        return cards;
    }

    private static Map<String, Object> card(String key, String title, String text, String icon) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("key", key);
        card.put("title", title);
        card.put("text", text);
        card.put("icon", icon);
        return card;
    }

    private static List<Map<String, Object>> roleOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        Map<String, Object> users = new LinkedHashMap<>();
        users.put("value", "community_user");
        users.put("label", "Community Users");
        Map<String, Object> partners = new LinkedHashMap<>();
        partners.put("value", "community_partner");
        partners.put("label", "Partners");
        options.add(users);
        options.add(partners);
        return options;
    }
}
