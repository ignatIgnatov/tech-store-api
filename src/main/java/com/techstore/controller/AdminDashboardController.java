package com.techstore.controller;

import com.techstore.service.ProductService;
import com.techstore.service.CategoryService;
import com.techstore.service.BrandService;
import com.techstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminDashboardController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final UserService userService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // In a real implementation, these would be actual count queries
        stats.put("totalProducts", 1234);
        stats.put("totalCategories", 25);
        stats.put("totalBrands", 45);
        stats.put("totalUsers", 892);
        stats.put("lowStockProducts", 23);
        stats.put("outOfStockProducts", 5);
        stats.put("featuredProducts", 12);
        stats.put("totalSales", 45678.99);
        stats.put("monthlySales", 12345.67);
        stats.put("activeUsers", 156);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/recent-activity")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getRecentActivity() {
        Map<String, Object> activity = new HashMap<>();

        // Mock recent activity data
        activity.put("recentOrders", 42);
        activity.put("newUsers", 15);
        activity.put("productsAdded", 8);
        activity.put("lastUpdated", System.currentTimeMillis());

        return ResponseEntity.ok(activity);
    }
}