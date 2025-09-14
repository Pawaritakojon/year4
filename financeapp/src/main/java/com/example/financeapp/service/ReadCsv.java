package com.example.financeapp.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

@Service
public class ReadCsv {

    public double getThailandInflation2024() {
        String csvFile = "C://Users//Daow Tanyapak//Desktop//pp//financeapp//financeapp//API_FP.CPI.TOTL.ZG_DS2_en_csv_v2_122376.csv";
        String line;

        int year2024Index = 67;

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            for (int i = 0; i < 4; i++) br.readLine();

            while ((line = br.readLine()) != null) {
                line = line.replaceAll("^\"|\"$", "");
                String[] data = line.split("\",\"");

                if (data.length > year2024Index) {
                    if (data[0].trim().equalsIgnoreCase("Thailand")) {
                        String inflationStr = data[year2024Index].trim();
                        System.out.println("ค่าเงินเฟ้อปี 2024 ของไทย: " + inflationStr);
                        try {
                            return Double.parseDouble(inflationStr);
                        } catch (NumberFormatException e) {
                            System.out.println("ไม่สามารถแปลงค่าเงินเฟ้อปี 2024: " + inflationStr);
                            return 0.0;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

}
