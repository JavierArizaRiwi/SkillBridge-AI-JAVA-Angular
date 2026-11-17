#!/bin/bash

# 🎬 DEMO COMMANDS - HU-22 & HU-15
# Ejecuta estos comandos durante la demo para mostrar funcionalidad

set -e

COLOR_GREEN='\033[0;32m'
COLOR_BLUE='\033[0;34m'
COLOR_YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${COLOR_BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${COLOR_BLUE}  DEMO COMMANDS: HU-22 (Analytics) & HU-15 (AI Migration)${NC}"
echo -e "${COLOR_BLUE}═══════════════════════════════════════════════════════${NC}"
echo ""

# ─────────────────────────────────────────────────────────────────
# HU-15: AI PROVIDER MIGRATION TESTS
# ─────────────────────────────────────────────────────────────────

demo_hu15_tests() {
    echo -e "${COLOR_YELLOW}[1/5] Running HU-15 Tests (AI Provider Migration)${NC}"
    echo -e "${COLOR_GREEN}Command:${NC}"
    echo "cd backend && mvn test -Dtest=GeminiAiAdapterTest,OpenAiAiAdapterTest -q"
    echo ""
    echo "Expected output:"
    echo "  ✅ GeminiAiAdapterTest: 8 tests PASSED"
    echo "  ✅ OpenAiAiAdapterTest: 8 tests PASSED"
    echo ""
    read -p "Press enter to run tests..."
    cd backend && mvn test -Dtest=GeminiAiAdapterTest,OpenAiAiAdapterTest -q || true
    echo ""
}

# ─────────────────────────────────────────────────────────────────
# HU-22: DEMAND ANALYTICS TESTS
# ─────────────────────────────────────────────────────────────────

demo_hu22_tests() {
    echo -e "${COLOR_YELLOW}[2/5] Running HU-22 Tests (Demand Analytics)${NC}"
    echo -e "${COLOR_GREEN}Command:${NC}"
    echo "cd backend && mvn test -Dtest=DemandAnalyticsServiceTest -q"
    echo ""
    echo "Expected output:"
    echo "  ✅ DemandAnalyticsServiceTest: 25+ tests PASSED"
    echo ""
    read -p "Press enter to run tests..."
    cd backend && mvn test -Dtest=DemandAnalyticsServiceTest -q || true
    echo ""
}

# ─────────────────────────────────────────────────────────────────
# API ENDPOINT: GET TRENDING SERVICES
# ─────────────────────────────────────────────────────────────────

demo_trending_api() {
    echo -e "${COLOR_YELLOW}[3/5] Testing Trending API Endpoint${NC}"
    echo -e "${COLOR_GREEN}Command:${NC}"
    echo "curl -X GET http://localhost:8080/api/trending \\"
    echo "  -H 'Authorization: Bearer <your-token>' \\"
    echo "  -H 'Content-Type: application/json' | jq ."
    echo ""
    echo "Expected response format:"
    echo "{"
    echo "  \"trendingServices\": ["
    echo "    {"
    echo "      \"offeringId\": \"uuid-xxx\","
    echo "      \"name\": \"Service Name\","
    echo "      \"trendScore\": 87.5,"
    echo "      \"forecast\": 25,"
    echo "      \"recommendations\": 120,"
    echo "      \"bookings\": 45,"
    echo "      \"conversionRate\": 0.375,"
    echo "      \"growthRate\": 0.67"
    echo "    }"
    echo "  ]"
    echo "}"
    echo ""
    read -p "Press enter to call API (make sure backend is running on :8080)..."
    curl -X GET http://localhost:8080/api/trending \
      -H 'Content-Type: application/json' 2>/dev/null | jq . 2>/dev/null || echo "⚠️  API not reachable or no token provided"
    echo ""
}

# ─────────────────────────────────────────────────────────────────
# DATABASE: CHECK RECOMMENDATION METRICS
# ─────────────────────────────────────────────────────────────────

demo_database_metrics() {
    echo -e "${COLOR_YELLOW}[4/5] Checking Database Metrics${NC}"
    echo -e "${COLOR_GREEN}SQL Query:${NC}"
    echo ""
    cat << 'SQL'
SELECT 
  date,
  offering_id,
  recommendations,
  bookings,
  cancellations,
  conversion_rate,
  growth_rate,
  forecast_7_days
FROM recommendation_daily_metrics 
ORDER BY date DESC, conversion_rate DESC 
LIMIT 10;
SQL
    echo ""
    echo -e "${COLOR_GREEN}Expected output:${NC}"
    echo "date        | offering_id           | recommendations | bookings | conversion_rate | growth_rate | forecast"
    echo "────────────┼───────────────────────┼─────────────────┼──────────┼─────────────────┼─────────────┼─────────"
    echo "2026-10-09  | 550e8400-e29b...      | 120             | 45       | 0.375           | 0.67        | 25"
    echo "2026-10-08  | 6ba7b810-9dad...      | 80              | 18       | 0.225           | 0.45        | 18"
    echo ""
}

# ─────────────────────────────────────────────────────────────────
# BUILD VERIFICATION
# ─────────────────────────────────────────────────────────────────

demo_build_verification() {
    echo -e "${COLOR_YELLOW}[5/5] Build Verification${NC}"
    echo -e "${COLOR_GREEN}Commands:${NC}"
    echo ""
    echo "1. Backend compilation:"
    echo "   cd backend && mvn clean compile -q"
    echo ""
    echo "2. Frontend build:"
    echo "   cd frontend && npm run build"
    echo ""
    echo "3. Full verify (tests + build):"
    echo "   cd backend && mvn clean verify -DskipITs"
    echo ""
    read -p "Press enter to verify backend build..."
    cd backend && mvn clean compile -q && echo -e "${COLOR_GREEN}✅ Backend compiled successfully${NC}" || echo "❌ Build failed"
    cd ../frontend && npm run build --silent 2>/dev/null && echo -e "${COLOR_GREEN}✅ Frontend built successfully${NC}" || echo "⚠️  Frontend build skipped (npm may not be installed)"
    echo ""
}

# ─────────────────────────────────────────────────────────────────
# FORMULAS EXPLANATION
# ─────────────────────────────────────────────────────────────────

demo_formulas() {
    echo -e "${COLOR_YELLOW}📊 FORMULAS REFERENCE${NC}"
    echo ""
    cat << 'FORMULAS'
┌─────────────────────────────────────────────────────────────────┐
│ 1. CONVERSION RATE                                              │
├─────────────────────────────────────────────────────────────────┤
│ conversionRate = effectiveBookings / recommendations             │
│ Example: 10 bookings / 40 recommendations = 0.25 (25%)          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 2. GROWTH RATE                                                  │
├─────────────────────────────────────────────────────────────────┤
│ growthRate = (recentBookings - previousBookings) / previousBookings
│ Example: (15 - 10) / 10 = 0.5 (50% growth)                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 3. TREND SCORE (Composite 0-100)                               │
├─────────────────────────────────────────────────────────────────┤
│ trendScore =                                                    │
│   (conversionRate × 100 × 0.50)        // 50% conversion       │
│ + (growthNormalized × 100 × 0.30)      // 30% growth           │
│ + (volumeRatio × 100 × 0.20)           // 20% volume           │
│                                                                  │
│ Example:                                                        │
│ - Conv: 0.25 × 100 × 0.50 = 12.5                              │
│ - Growth: 0.30 × 100 × 0.30 = 9                               │
│ - Volume: 0.8 × 100 × 0.20 = 16                               │
│ ────────────────────────────────────                           │
│   TREND SCORE = 37.5 / 100                                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 4. FORECAST (Next 7 Days)                                      │
├─────────────────────────────────────────────────────────────────┤
│ forecast = avgDailyBookings × 7 × amplifier                     │
│ Where: amplifier = 1 + max(0, growthRate)                      │
│                                                                  │
│ Example (with 50% growth):                                     │
│ - Avg daily: 2 bookings                                        │
│ - amplifier: 1 + 0.5 = 1.5                                     │
│ - forecast: 2 × 7 × 1.5 = 21 bookings                         │
└─────────────────────────────────────────────────────────────────┘
FORMULAS
    echo ""
}

# ─────────────────────────────────────────────────────────────────
# MAIN MENU
# ─────────────────────────────────────────────────────────────────

main_menu() {
    clear
    echo -e "${COLOR_BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${COLOR_BLUE}  DEMO COMMANDS MENU - HU-22 & HU-15${NC}"
    echo -e "${COLOR_BLUE}═══════════════════════════════════════════════════════${NC}"
    echo ""
    echo "Choose an option:"
    echo ""
    echo "  1) HU-15 Tests (AI Provider Migration)"
    echo "  2) HU-22 Tests (Demand Analytics)"
    echo "  3) API: Test Trending Endpoint"
    echo "  4) Database: Check Metrics"
    echo "  5) Build Verification"
    echo "  6) Show Formulas Reference"
    echo "  7) Run Full Demo (All steps)"
    echo "  8) Exit"
    echo ""
    read -p "Select option (1-8): " option
    
    case $option in
        1) demo_hu15_tests; main_menu ;;
        2) demo_hu22_tests; main_menu ;;
        3) demo_trending_api; main_menu ;;
        4) demo_database_metrics; main_menu ;;
        5) demo_build_verification; main_menu ;;
        6) demo_formulas; main_menu ;;
        7) 
            demo_hu15_tests
            demo_hu22_tests
            demo_build_verification
            demo_formulas
            demo_trending_api
            demo_database_metrics
            main_menu
            ;;
        8) 
            echo -e "${COLOR_GREEN}✅ Demo completed!${NC}"
            echo ""
            exit 0
            ;;
        *) 
            echo -e "${COLOR_YELLOW}Invalid option. Please try again.${NC}"
            sleep 1
            main_menu
            ;;
    esac
}

# ─────────────────────────────────────────────────────────────────
# QUICK START
# ─────────────────────────────────────────────────────────────────

if [ "$1" == "quick" ]; then
    echo -e "${COLOR_GREEN}Running Quick Demo...${NC}"
    demo_hu15_tests
    demo_hu22_tests
    demo_build_verification
    demo_formulas
elif [ "$1" == "api" ]; then
    demo_trending_api
elif [ "$1" == "formulas" ]; then
    demo_formulas
else
    main_menu
fi
