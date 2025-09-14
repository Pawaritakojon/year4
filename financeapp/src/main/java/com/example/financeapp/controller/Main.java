package com.example.financeapp.controller;

import com.example.financeapp.dp.User;
import com.example.financeapp.repository.UserInfoRepository;
import com.example.financeapp.repository.UserRepository;
import com.example.financeapp.service.ApiService;
import com.example.financeapp.service.ReadCsv;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.web.servlet.error.ErrorController;
import java.text.NumberFormat;
import java.util.Locale;
import java.text.DecimalFormat;

import java.util.Map;
import java.util.Optional;

@Controller
public class Main implements ErrorController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private ReadCsv readCsv;

    private final ApiService apiService;

    @Autowired
    public Main(ApiService apiService) {
        this.apiService = apiService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping("/home")
    public String homePage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user != null) {
            model.addAttribute("user", user);
        }

        return "home";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("error", "");
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.getPassword().equals(password)) {
                session.setAttribute("user", user);
                return "redirect:/home";
            }
        }
        model.addAttribute("error", "Invalid username or password");
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        return "login";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username,
                               @RequestParam String fullName,
                               @RequestParam String email,
                               @RequestParam String password,
                               Model model) {
        // Check if username already exists
        if (userRepository.existsByUsername(username)) {
            model.addAttribute("error", "Username already exists");
            return "login";
        }

        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(password);
        user.setRole("USER");

        userRepository.save(user);
        return "redirect:/login?success=true";
    }

    // แทนที่เมธอดเดิม
    @PostMapping("/logout")
    public String logoutPost(HttpSession session) {
        session.invalidate();
        return "redirect:/login?logout"; // กลับหน้า login พร้อมพารามิเตอร์บอกว่าออกจากระบบแล้ว
    }

    @GetMapping("/logout")
    public String logoutGet(HttpSession session) {
        session.invalidate();
        return "redirect:/login?logout";
    }


    @GetMapping("/exchange")
    public String showRates(@RequestParam(defaultValue = "THB") String currency, Model model) {
        try {
            Map<String, Object> rates = apiService.getExchangeRates();
            model.addAttribute("rates", rates);
            model.addAttribute("selectedCurrency", currency);
            model.addAttribute("rate", rates.get(currency));
            return "exchange";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    @GetMapping("/caluserinfo")
    public String showUserInfoForm(Model model) {
        model.addAttribute("userinfo", new com.example.financeapp.dp.UserInfo());
        return "caluserinfo";
    }

    @PostMapping("/caluserinfo")
    public String calculateinfo(@ModelAttribute("userinfo") com.example.financeapp.dp.UserInfo userInfo, Model model) {
        userInfoRepository.save(userInfo);

        double inflationRate = readCsv.getThailandInflation2024();
        double salary = userInfo.getSalary();
        double adjustedSalary = salary / (1 + inflationRate / 100.0);
        adjustedSalary = Math.round(adjustedSalary * 100.0) / 100.0;

        int currentAge = userInfo.getAge();
        int retirementAge = userInfo.getRetirementAge();
        int yearsToRetirement = retirementAge - currentAge;

        double desiredAmount = userInfo.getDesiredRetirementAmount();
        double futureValue = desiredAmount * Math.pow(1 + inflationRate / 100.0, yearsToRetirement);
        futureValue = Math.round(futureValue * 100.0) / 100.0;

        int totalMonths = yearsToRetirement * 12;
        double monthlySaving = futureValue / totalMonths;
        monthlySaving = Math.round(monthlySaving * 100.0) / 100.0;

        Locale thaiLocale = new Locale("th", "TH");
        NumberFormat nf = NumberFormat.getNumberInstance(thaiLocale);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");
        DecimalFormat percentFormat = new DecimalFormat("0.0");

        model.addAttribute("userInfo", userInfo);
        model.addAttribute("inflationRate", inflationRate);
        model.addAttribute("adjustedSalary", adjustedSalary);
        model.addAttribute("yearsToRetirement", yearsToRetirement);
        model.addAttribute("futureValue", futureValue);
        model.addAttribute("monthlySaving", monthlySaving);

        model.addAttribute("formattedDesiredAmount", moneyFormat.format(desiredAmount));
        model.addAttribute("formattedFutureValue", moneyFormat.format(futureValue));
        model.addAttribute("formattedMonthlySaving", moneyFormat.format(monthlySaving));
        model.addAttribute("formattedAdjustedSalary", moneyFormat.format(adjustedSalary));
        model.addAttribute("formattedSalary", moneyFormat.format(userInfo.getSalary()));
        model.addAttribute("formattedInflationRate", percentFormat.format(inflationRate));

        return "result";
    }

    @GetMapping("/result")
    public String resultGuard() {
        return "redirect:/caluserinfo?msg=pleaseInput";
    }

    @GetMapping("/ourinfo")
    public String showOurInfo(Model model) {
        model.addAttribute("userinfo", new com.example.financeapp.dp.UserInfo());
        return "ourinfo";
    }

    @RequestMapping("/error")
    public String handleError(Model model) {
        return "error";
    }
}
