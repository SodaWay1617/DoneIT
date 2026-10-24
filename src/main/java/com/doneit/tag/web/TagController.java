package com.doneit.tag.web;

import com.doneit.tag.application.TagService;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TagController {
    private final TagService tags;
    private final Clock clock;
    public TagController(TagService tags, Clock clock) { this.tags=tags; this.clock=clock; }

    @GetMapping("/tags")
    public String list(Model model) {
        model.addAttribute("tags",tags.list());
        return "tags";
    }

    @GetMapping("/tags/{id}")
    public String detail(@PathVariable Long id,@RequestParam(required=false) String month,Model model) {
        YearMonth selected;
        try { selected=month==null||month.isBlank()?YearMonth.now(clock):YearMonth.parse(month); }
        catch(DateTimeParseException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        if(selected.getYear()<1900||selected.getYear()>9998) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        model.addAttribute("detail",tags.detail(id,selected));
        return "tag-detail";
    }

    @PostMapping("/tags")
    public String create(@RequestParam String name, RedirectAttributes flash) {
        try { return "redirect:/tags/"+tags.create(name).id(); }
        catch(IllegalArgumentException exception) { flash.addFlashAttribute("tagError","tag.invalid"); return "redirect:/tags"; }
    }

    @PostMapping("/tags/{id}")
    public String rename(@PathVariable Long id,@RequestParam String name,RedirectAttributes flash) {
        try { tags.rename(id,name); }
        catch(IllegalArgumentException exception) { flash.addFlashAttribute("tagError","tag.invalid"); }
        catch(DataIntegrityViolationException exception) { flash.addFlashAttribute("tagError","tag.conflict"); }
        return "redirect:/tags";
    }

    @PostMapping("/tags/{id}/delete")
    public String delete(@PathVariable Long id) { tags.delete(id); return "redirect:/tags"; }
}
