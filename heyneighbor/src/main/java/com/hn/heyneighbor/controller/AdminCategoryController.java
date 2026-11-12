package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.CategoryRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class AdminCategoryController {

    private static final List<String> SHOW = List.of("all", "active", "inactive");
    private static final int NAME_MAX = 80;
    private static final int DESCRIPTION_MAX = 500;

    private final CategoryRepository categories;

    public AdminCategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping("/admin/categories")
    public String page(@RequestParam(name = "show", required = false) String show,
                       @RequestParam(name = "edit", required = false) Integer edit,
                       @RequestParam(name = "new", required = false) String isNew,
                       Model model) {
        String showFilter = SHOW.contains(show) ? show : "all";

        if (edit != null) {
            Optional<Map<String, Object>> found = categories.find(edit);
            if (found.isPresent()) {
                return render(model, showFilter, "edit", formFrom(found.get()), null);
            }
            model.addAttribute("error", "That category no longer exists.");
        } else if (isNew != null) {
            Map<String, Object> form = new HashMap<>();
            form.put("id", null);
            form.put("name", "");
            form.put("description", "");
            form.put("parentId", null);
            form.put("active", true);
            return render(model, showFilter, "new", form, null);
        }
        return render(model, showFilter, null, null, null);
    }

    @PostMapping("/admin/categories")
    public String create(@RequestParam(name = "name", required = false) String name,
                         @RequestParam(name = "description", required = false) String description,
                         @RequestParam(name = "parentId", required = false) String parentId,
                         @RequestParam(name = "active", required = false) String active,
                         RedirectAttributes redirect, Model model) {
        Map<String, Object> form = formFromInput(null, name, description, parentId, active);
        String error = validate(null, form);
        if (error == null) {
            try {
                int id = categories.create((String) form.get("name"), blankToNull((String) form.get("description")),
                        (Integer) form.get("parentId"), (Boolean) form.get("active"));
                redirect.addFlashAttribute("message", "Category \"" + form.get("name") + "\" was added.");
                return "redirect:/admin/categories?edit=" + id;
            } catch (DuplicateKeyException e) {
                error = "A category with that name already exists.";
            }
        }
        return render(model, "all", "new", form, error);
    }

    @PostMapping("/admin/categories/{id}")
    public String save(@PathVariable int id,
                       @RequestParam(name = "name", required = false) String name,
                       @RequestParam(name = "description", required = false) String description,
                       @RequestParam(name = "parentId", required = false) String parentId,
                       @RequestParam(name = "active", required = false) String active,
                       RedirectAttributes redirect, Model model) {
        if (categories.find(id).isEmpty()) {
            redirect.addFlashAttribute("error", "That category no longer exists.");
            return "redirect:/admin/categories";
        }
        Map<String, Object> form = formFromInput(id, name, description, parentId, active);
        String error = validate(id, form);
        if (error == null) {
            try {
                categories.update(id, (String) form.get("name"), blankToNull((String) form.get("description")),
                        (Integer) form.get("parentId"), (Boolean) form.get("active"));
                redirect.addFlashAttribute("message", "Changes to \"" + form.get("name") + "\" were saved.");
                return "redirect:/admin/categories?edit=" + id;
            } catch (DuplicateKeyException e) {
                error = "A category with that name already exists.";
            }
        }
        return render(model, "all", "edit", form, error);
    }

    @PostMapping("/admin/categories/{id}/deactivate")
    public String deactivate(@PathVariable int id, RedirectAttributes redirect) {
        return setActive(id, false, redirect);
    }

    @PostMapping("/admin/categories/{id}/activate")
    public String activate(@PathVariable int id, RedirectAttributes redirect) {
        return setActive(id, true, redirect);
    }

    private String setActive(int id, boolean active, RedirectAttributes redirect) {
        Optional<Map<String, Object>> found = categories.find(id);
        if (found.isEmpty()) {
            redirect.addFlashAttribute("error", "That category no longer exists.");
            return "redirect:/admin/categories";
        }
        categories.setActive(id, active);
        redirect.addFlashAttribute("message",
                "Category \"" + found.get().get("name") + "\" was " + (active ? "reactivated." : "deactivated."));
        return "redirect:/admin/categories?edit=" + id;
    }

    private String render(Model model, String show, String mode, Map<String, Object> form, String error) {
        List<Map<String, Object>> all = categories.list("all");
        model.addAttribute("pageTitle", "Manage Categories");
        model.addAttribute("rows", categories.list(show));
        model.addAttribute("parents", all);
        model.addAttribute("counts", categories.counts());
        model.addAttribute("show", show);
        model.addAttribute("mode", mode);
        model.addAttribute("form", form);
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "AdminCategories";
    }

    private static Map<String, Object> formFrom(Map<String, Object> row) {
        Map<String, Object> form = new HashMap<>();
        form.put("id", ((Number) row.get("category_id")).intValue());
        form.put("name", row.get("name"));
        form.put("description", row.get("description") == null ? "" : row.get("description"));
        form.put("parentId", row.get("parent_category_id") == null
                ? null : ((Number) row.get("parent_category_id")).intValue());
        form.put("active", Boolean.TRUE.equals(row.get("is_active")));
        return form;
    }

    private static Map<String, Object> formFromInput(Integer id, String name, String description,
                                                     String parentId, String active) {
        Map<String, Object> form = new HashMap<>();
        form.put("id", id);
        String cleanName = Options.text(name);
        String cleanDescription = Options.text(description);
        form.put("name", cleanName == null ? "" : cleanName);
        form.put("description", cleanDescription == null ? "" : cleanDescription);
        Integer parent = null;
        String parentText = Options.text(parentId);
        if (parentText != null) {
            try {
                parent = Integer.valueOf(parentText);
            } catch (NumberFormatException e) {
                parent = -1;   // fails validation below
            }
        }
        form.put("parentId", parent);
        form.put("active", active != null);
        return form;
    }

    private String validate(Integer id, Map<String, Object> form) {
        String name = (String) form.get("name");
        String description = (String) form.get("description");
        Integer parent = (Integer) form.get("parentId");

        if (name.isEmpty()) {
            return "Category name is required.";
        }
        if (name.length() > NAME_MAX) {
            return "Category name must be " + NAME_MAX + " characters or fewer.";
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            return "Category name cannot contain control characters.";
        }
        if (description.length() > DESCRIPTION_MAX) {
            return "Description must be " + DESCRIPTION_MAX + " characters or fewer.";
        }
        if (categories.nameTaken(name, id)) {
            return "A category with that name already exists.";
        }
        if (parent != null) {
            if (categories.find(parent).isEmpty()) {
                return "The parent category you chose does not exist.";
            }
            if (id != null && categories.wouldCreateLoop(id, parent)) {
                return "A category cannot be its own parent or sit under one of its own sub-categories.";
            }
        }
        return null;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
