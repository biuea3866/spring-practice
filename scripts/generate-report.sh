#!/bin/bash

REPORT_FILE="docs/PERFORMANCE_REPORT.md"

echo "# Performance Report - Spring Local Cache Test" > ${REPORT_FILE}
echo "" >> ${REPORT_FILE}
echo "## 1. Test Environment" >> ${REPORT_FILE}
echo "- Data Scale: 100,000 products, 50,000 orders" >> ${REPORT_FILE}
echo "- Load Test: k6 (max 50 VU)" >> ${REPORT_FILE}
echo "- Test Duration: 2 minutes per scenario" >> ${REPORT_FILE}
echo "- Cache TTL: 60 seconds (1 minute)" >> ${REPORT_FILE}
echo "" >> ${REPORT_FILE}

echo "## 2. Cache Settings" >> ${REPORT_FILE}
echo "| Cache Name | TTL | Null Cache TTL | Max Size |" >> ${REPORT_FILE}
echo "|---|---|---|---|" >> ${REPORT_FILE}
echo "| Product Detail | 60s | 10s | 10,000 |" >> ${REPORT_FILE}
echo "| Product List | 60s | 10s | 100 |" >> ${REPORT_FILE}
echo "| Order List | 60s | 10s | 5,000 |" >> ${REPORT_FILE}
echo "" >> ${REPORT_FILE}

echo "## 3. Performance Comparison Results" >> ${REPORT_FILE}
echo "### Test 1: Cache OFF vs ON" >> ${REPORT_FILE}
echo "*(Detailed metrics from k6 and Prometheus would be inserted here)*" >> ${REPORT_FILE}
echo "" >> ${REPORT_FILE}

echo "### Test 2: Cache Penetration Prevention Effect" >> ${REPORT_FILE}
echo "*(Detailed metrics from k6 and Prometheus would be inserted here)*" >> ${REPORT_FILE}
echo "" >> ${REPORT_FILE}

echo "## 4. Key Findings" >> ${REPORT_FILE}
echo "*(Summary of findings based on collected metrics)*" >> ${REPORT_FILE}
echo "" >> ${REPORT_FILE}

echo "## 5. Visualization" >> ${REPORT_FILE}
echo "*(Grafana dashboard screenshots would be embedded here)*" >> ${REPORT_FILE}
echo "" >> ${REPORT_FILE}

echo "## 6. Impact of 1-minute TTL Setting" >> ${REPORT_FILE}
echo "*(Analysis of TTL impact)*" >> ${REPORT_FILE}
echo "" >> ${REPORT_FILE}

echo "## 7. Conclusion" >> ${REPORT_FILE}
echo "*(Overall conclusion)*" >> ${REPORT_FILE}
echo "" >> ${REPORT_FILE}

echo "Report generated at ${REPORT_FILE}"
