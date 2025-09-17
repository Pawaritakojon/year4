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
    public String calculateinfo(@ModelAttribute com.example.financeapp.dp.UserInfo userInfo,
                                Model model) {

        // ---------- 1) รับอินพุต ----------
        int currentAge = userInfo.getAge();
        int retirementAge = userInfo.getRetirementAge();
        int yearsToRetirement = Math.max(0, retirementAge - currentAge);
        int totalMonths = yearsToRetirement * 12;
        if (yearsToRetirement <= 0) {
            model.addAttribute("error", "อายุเกษียณต้องมากกว่าอายุปัจจุบัน");
            return "result";
        }

        double salaryNow    = nz(userInfo.getSalary());
        double desiredToday = nz(userInfo.getDesiredRetirementAmount());

        // ค่าที่ผู้ใช้กรอก (บาท/เดือน) — ถ้า null ให้เป็น 0
        double ssInput  = nz(userInfo.getSocialSecurityInput());
        double pvdInput = nz(userInfo.getProvidentFundInput());

        // ---------- 2) สมมติฐาน ----------
        double inflationAnnual    = 0.025;
        double returnAnnual       = 0.06;
        double salaryGrowthAnnual = 0.047;   // ✅ ตามที่ขอ 4.7%
        int    jobChangeIntervalY = 3;
        double jobChangeRaisePct  = 0.25;
        double jobChangeProb      = 0.60;

        // ---------- 3) เป้าหมายมูลค่าอนาคต ----------
        double futureTarget = desiredToday * Math.pow(1 + inflationAnnual, yearsToRetirement);

        // ---------- 4) ทบต้นรายเดือน ----------
        double r = Math.pow(1 + returnAnnual, 1.0 / 12.0) - 1.0;

        // ---------- 5) เส้นเงินเดือนรายเดือน ----------
        double[] salaryPath = new double[totalMonths];
        double currentSalary = salaryNow;

        for (int year = 0; year < yearsToRetirement; year++) {
            if (year > 0) currentSalary *= (1.0 + salaryGrowthAnnual);
            if (jobChangeIntervalY > 0 && year > 0 && (year % jobChangeIntervalY == 0)) {
                currentSalary *= (1.0 + jobChangeProb * jobChangeRaisePct);
            }
            for (int m = 0; m < 12; m++) {
                int idx = year * 12 + m;
                if (idx >= totalMonths) break;
                salaryPath[idx] = currentSalary;
            }
        }

        // ---------- 6) หา % ออม (binary search) ----------
        java.util.function.DoubleFunction<Double> fvFromRate = (savingRate) -> {
            double fv = 0.0;
            for (int m = 0; m < totalMonths; m++) {
                // หักค่าคงที่ตามที่ผู้ใช้กรอก: SS/PVD (ไม่ใช้สูตร 5%)
                double baseNet = Math.max(0, salaryPath[m] - ssInput - pvdInput);
                double contribYou = savingRate * baseNet;
                double contribPvd = pvdInput;         // นับเป็นเงินเข้าพอร์ตด้วย (เพราะเป็น “เงินคุณออม”)
                fv *= (1 + r);
                fv += contribYou + contribPvd;
            }
            return fv;
        };

        double lo = 0.0, hi = 1.0;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2.0;
            if (fvFromRate.apply(mid) >= futureTarget) hi = mid; else lo = mid;
        }
        double savingRate = hi;

        // ---------- 7) เดือนแรก ----------
        double baseNet1  = Math.max(0, salaryPath[0] - ssInput - pvdInput);
        double monthlySavingYou = savingRate * baseNet1;
        double monthlyPvd       = pvdInput;
        double totalContribM1   = monthlySavingYou + monthlyPvd;

        // ---------- 8) series รายเดือน ----------
        java.util.List<Integer> chartMonths  = new java.util.ArrayList<>();
        java.util.List<Integer> chartAges    = new java.util.ArrayList<>();
        java.util.List<Double>  chartSalary  = new java.util.ArrayList<>();
        java.util.List<Double>  chartSSF     = new java.util.ArrayList<>();
        java.util.List<Double>  chartPVD     = new java.util.ArrayList<>();
        java.util.List<Double>  chartBase    = new java.util.ArrayList<>();
        java.util.List<Double>  chartSaving  = new java.util.ArrayList<>();
        java.util.List<Double>  chartBalance = new java.util.ArrayList<>();

        double balanceAcc = 0.0;
        for (int m = 0; m < totalMonths; m++) {
            int monthNo = m + 1;
            int ageThisMonth = currentAge + (m / 12);

            double ssfM  = ssInput;
            double pvdM  = pvdInput;
            double baseM = Math.max(0, salaryPath[m] - ssfM - pvdM);
            double saveM = savingRate * baseM;

            balanceAcc *= (1 + r);
            balanceAcc += (saveM + pvdM);

            chartMonths.add(monthNo);
            chartAges.add(ageThisMonth);
            chartSalary.add(round2(salaryPath[m]));
            chartSSF.add(round2(ssfM));
            chartPVD.add(round2(pvdM));
            chartBase.add(round2(baseM));
            chartSaving.add(round2(saveM));
            chartBalance.add(round2(balanceAcc));
        }

        // ---------- 9) สรุปรายปี ----------
        java.util.List<java.util.Map<String,Object>> yearSummary = new java.util.ArrayList<>();
        int yearCount = yearsToRetirement;
        int age = currentAge;
        int idx = 0;
        for (int y = 1; y <= yearCount; y++) {
            double sumSaving = 0, sumSSF = 0, sumSalary = 0;
            for (int m = 0; m < 12 && idx < totalMonths; m++, idx++) {
                sumSaving += chartSaving.get(idx) + chartPVD.get(idx); // รวมเงินคุณออม + PVD
                sumSSF    += chartSSF.get(idx);
                sumSalary += chartSalary.get(idx);
            }
            java.util.Map<String,Object> row = new java.util.HashMap<>();
            row.put("yearIndex", y);
            row.put("ageEnd", ++age);
            row.put("avgSalary", (sumSalary / 12.0));
            row.put("sumSSF", sumSSF);
            row.put("sumSaving", sumSaving);
            row.put("avgSavingPerMonth", sumSaving / 12.0);
            row.put("endBalance", chartBalance.get(Math.min(chartBalance.size()-1, y*12 - 1)));
            yearSummary.add(row);
        }

        // ---------- 10) ใส่ค่าใน model ----------
        java.text.DecimalFormat money = new java.text.DecimalFormat("#,##0.00");
        java.text.DecimalFormat pct1  = new java.text.DecimalFormat("0.0");

        model.addAttribute("userInfo", userInfo);
        model.addAttribute("yearsToRetirement", yearsToRetirement);
        model.addAttribute("totalMonths", totalMonths);

        model.addAttribute("formattedSalaryNow", money.format(salaryNow));
        model.addAttribute("formattedDesiredAmountToday", money.format(desiredToday));
        model.addAttribute("formattedFutureTarget", money.format(round2(futureTarget)));
        model.addAttribute("formattedSavingRatePct", pct1.format(savingRate * 100.0));
        model.addAttribute("formattedMonthlySavingYou", money.format(round2(monthlySavingYou)));
        model.addAttribute("formattedMonthlySavingTotalStart", money.format(round2(totalContribM1)));

        // ✅ ใส่ค่าที่หน้า result ใช้แน่ๆ
        model.addAttribute("formattedSocialSecurityUsed", money.format(round2(ssInput)));
        model.addAttribute("formattedProvidentFundUsed", money.format(round2(pvdInput)));
        // (ถ้าอยากโชว์ค่าที่ผู้ใช้กรอกแบบดิบๆ เพิ่มบรรทัดนี้ได้)
        model.addAttribute("formattedSocialSecurityInput", money.format(round2(ssInput)));
        model.addAttribute("formattedProvidentFundInput", money.format(round2(pvdInput)));

        model.addAttribute("formattedInflationAnnual", pct1.format(inflationAnnual * 100.0));
        model.addAttribute("formattedRAnnual", pct1.format(returnAnnual * 100.0));
        model.addAttribute("formattedGAnnual", pct1.format(salaryGrowthAnnual * 100.0));
        model.addAttribute("formattedJobChangeProbPct", pct1.format(jobChangeProb * 100.0));
        model.addAttribute("jobChangeRaisePct", jobChangeRaisePct);

        // series
        model.addAttribute("chartMonths",  chartMonths);
        model.addAttribute("chartAges",    chartAges);
        model.addAttribute("chartSalary",  chartSalary);
        model.addAttribute("chartSSF",     chartSSF);
        model.addAttribute("chartPVD",     chartPVD);   // ✅ อย่าลืมส่ง
        model.addAttribute("chartBase",    chartBase);
        model.addAttribute("chartSaving",  chartSaving);
        model.addAttribute("chartBalance", chartBalance);

        model.addAttribute("yearSummary", yearSummary);

        return "result";
    }

    private static double nz(Double v) { return v == null ? 0.0 : v; }
    private static double round2(double x){ return Math.round(x * 100.0) / 100.0; }


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
