package net.enjoy.springboot.registrationlogin.controller;

import jakarta.servlet.http.HttpSession;
import net.enjoy.springboot.registrationlogin.dto.DiscussionDto;
import net.enjoy.springboot.registrationlogin.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;

import net.enjoy.springboot.registrationlogin.service.DiscussionService;
import net.enjoy.springboot.registrationlogin.entity.Discussion;

@Controller
public class HomeController {

    private final DiscussionService discussionService;
    private final ReportService reportService;

    public HomeController(DiscussionService discussionService, ReportService reportService) {
        this.discussionService = discussionService;
        this.reportService = reportService;
    }
    // ✅ Home Page Route (Default landing page)
    @GetMapping("/")
    public String showHomePage(HttpServletResponse response) {
        setNoCacheHeaders(response);
        return "home";  // This loads home.html as the default page
    }

    // ✅ Organizer Page Route
    @GetMapping("/organizer")
    public String showOrganizerPage(HttpServletResponse response) {
        setNoCacheHeaders(response);
        return "index";  // Organizer button takes user to index.html (Login page)
    }

    // ✅ Organizer Dashboard Route
    @GetMapping("/organizer-dashboard")
    public String showOrganizerDashboard(HttpServletResponse response) {
        setNoCacheHeaders(response);
        return "organizer";  // On successful login → organizer.html (Dashboard)
    }

    // ✅ User Page Route
    @GetMapping("/user")
    public String showUserPage(HttpServletResponse response) {
        setNoCacheHeaders(response);
        return "user";  // User button takes user to user.html (Discussion Name & Unique Code)
    }

    // ✅ Discussion Page Route
    @GetMapping("/discussion")
    public String showDiscussionPage(@RequestParam Long discussionId,
                                     HttpSession session,
                                     Model model,
                                     HttpServletResponse response) {
        setNoCacheHeaders(response);

        Boolean canAccess = (Boolean) session.getAttribute("canAccessDiscussion_" + discussionId);
        if (canAccess == null || !canAccess) {
            return "redirect:/user?error=unauthorized";
        }

        Discussion discussion = discussionService.findDiscussionById(discussionId);

        if (discussion == null) {
            model.addAttribute("error", "Discussion not found.");
            return "error";
        }

        model.addAttribute("discussion", discussion);
        return "discussion"; // Show the specific discussion page
    }


    @GetMapping("/past")
    public String showPastDiscussionsPage(HttpServletResponse response) {
        setNoCacheHeaders(response);
        return "past";
    }

    // ✅ Utility function to prevent caching
    private void setNoCacheHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0
        response.setHeader("Expires", "0"); // Proxies
    }

    @GetMapping("/update")
    public String showUpdateDiscussionPage(@RequestParam Long discussionId,
                                           Model model,
                                           HttpServletResponse response) {
        setNoCacheHeaders(response);

        Discussion discussion = discussionService.findDiscussionById(discussionId);

        if (discussion == null) {
            model.addAttribute("error", "❌ Discussion not found.");
            return "error";
        }

        model.addAttribute("discussion", discussion);
        return "update-discussion";  // Load update-discussion.html page
    }

    @GetMapping("/result")
    public String showResultPage(@RequestParam Long discussionId,
                                 Model model,
                                 HttpServletResponse response) {
        setNoCacheHeaders(response);

        DiscussionDto discussionDto = discussionService.getDiscussionById(discussionId);

        if (discussionDto == null) {
            model.addAttribute("error", "❌ Discussion not found.");
            return "error";
        }

        model.addAttribute("discussion", discussionDto);
        return "result";  // Loads result.html
    }

    @GetMapping("/report")
    public String showReportPage(@RequestParam Long discussionId,
                                 Model model,
                                 HttpServletResponse response) {
        setNoCacheHeaders(response);

        DiscussionDto discussionDto = reportService.getDiscussionReportById(discussionId);

        if (discussionDto == null) {
            model.addAttribute("error", "❌ Discussion not found.");
            return "error";
        }

        model.addAttribute("discussion", discussionDto);
        return "report";  // Loads report.html
    }

}
