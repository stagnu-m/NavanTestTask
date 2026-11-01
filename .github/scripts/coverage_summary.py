#!/usr/bin/env python3
import glob
import os
import xml.etree.ElementTree as ET

# --- 1. PARSE JUNIT TEST RESULTS ---
test_results_dir = 'build/test-results/test'
xml_files = sorted(glob.glob(os.path.join(test_results_dir, 'TEST-*.xml')))

total_tests = 0
total_failures = 0
total_errors = 0
total_skipped = 0
total_time = 0.0
suites = []
failed_testcases = []

for xml_file in xml_files:
    try:
        tree = ET.parse(xml_file)
        root = tree.getroot()
        name = root.attrib.get('name', os.path.basename(xml_file))
        tests = int(root.attrib.get('tests', 0))
        failures = int(root.attrib.get('failures', 0))
        errors = int(root.attrib.get('errors', 0))
        skipped = int(root.attrib.get('skipped', 0))
        time_taken = float(root.attrib.get('time', 0.0))

        passed = tests - failures - errors - skipped
        total_tests += tests
        total_failures += failures
        total_errors += errors
        total_skipped += skipped
        total_time += time_taken

        short_name = name.split('.')[-1]
        suite_type = '🌐 Integration' if 'Integration' in short_name else '⚙️ Unit'
        suites.append({
            'name': short_name,
            'type': suite_type,
            'tests': tests,
            'passed': passed,
            'failed': failures + errors,
            'skipped': skipped,
            'time': time_taken,
            'status': '✅ Pass' if (failures + errors) == 0 else '❌ Fail'
        })

        for tc in root.findall('testcase'):
            tc_name = tc.attrib.get('name', 'unknown')
            tc_class = tc.attrib.get('classname', short_name).split('.')[-1]
            failure = tc.find('failure')
            if failure is not None:
                failed_testcases.append({
                    'class': tc_class,
                    'name': tc_name,
                    'message': failure.attrib.get('message', failure.text or '')[:150]
                })
    except Exception as e:
        print(f"Warning: could not parse {xml_file}: {e}")

total_passed = total_tests - total_failures - total_errors - total_skipped
status_badge = '✅ ALL TESTS PASSED' if (total_failures + total_errors) == 0 else '❌ TESTS FAILED'

test_summary_md = f"""## 🧪 Test Execution Summary ({status_badge})

| Metric | Result |
|---|---|
| **Total Tests** | **{total_tests}** |
| **Passed** | **{total_passed}** ✅ |
| **Failed** | **{total_failures + total_errors}** {'❌' if (total_failures + total_errors) > 0 else '✅'} |
| **Skipped** | **{total_skipped}** |
| **Total Duration** | **{total_time:.2f}s** |

### Suite Breakdown (Unit & Integration Tests)

| Test Suite | Category | Tests | Passed | Failed | Skipped | Duration | Status |
|---|---|---|---|---|---|---|---|
"""

for s in suites:
    test_summary_md += f"| `{s['name']}` | {s['type']} | {s['tests']} | {s['passed']} | {s['failed']} | {s['skipped']} | {s['time']:.2f}s | {s['status']} |\n"

test_summary_md += f"| **Total** | | **{total_tests}** | **{total_passed}** | **{total_failures + total_errors}** | **{total_skipped}** | **{total_time:.2f}s** | **{status_badge}** |\n\n"

if failed_testcases:
    test_summary_md += "### ❌ Failed Tests\n\n| Suite | Test Case | Failure Details |\n|---|---|---|\n"
    for f in failed_testcases:
        test_summary_md += f"| `{f['class']}` | `{f['name']}` | {f['message']} |\n"
    test_summary_md += "\n"

# --- 2. PARSE JACOCO CODE COVERAGE ---
coverage_md = ""
report_path = 'build/reports/jacoco/test/jacocoTestReport.xml'
if os.path.exists(report_path):
    tree = ET.parse(report_path)
    root = tree.getroot()

    stats = {}
    for counter in root.findall('counter'):
        c_type = counter.get('type')
        missed = int(counter.get('missed'))
        covered = int(counter.get('covered'))
        total = missed + covered
        pct = (covered / total * 100) if total > 0 else 0
        stats[c_type] = (covered, total, pct)

    instruction = stats.get('INSTRUCTION', (0, 0, 0))
    line = stats.get('LINE', (0, 0, 0))
    branch = stats.get('BRANCH', (0, 0, 0))
    complexity = stats.get('COMPLEXITY', (0, 0, 0))
    method = stats.get('METHOD', (0, 0, 0))
    cls_stat = stats.get('CLASS', (0, 0, 0))

    coverage_md = f"""## 📊 JaCoCo Code Coverage Report

| Metric | Covered | Total | Coverage % | Status (Min 80%) |
|---|---|---|---|---|
| **Instruction** | {instruction[0]:,} | {instruction[1]:,} | **{instruction[2]:.2f}%** | {'✅ Pass' if instruction[2] >= 80 else '❌ Fail'} |
| **Line** | {line[0]:,} | {line[1]:,} | **{line[2]:.2f}%** | {'✅ Pass' if line[2] >= 80 else '❌ Fail'} |
| **Branch** | {branch[0]:,} | {branch[1]:,} | **{branch[2]:.2f}%** | {'✅ Pass' if branch[2] >= 80 else '❌ Fail'} |
| **Complexity** | {complexity[0]:,} | {complexity[1]:,} | **{complexity[2]:.2f}%** | {'✅ Pass' if complexity[2] >= 80 else '❌ Fail'} |
| **Method** | {method[0]:,} | {method[1]:,} | **{method[2]:.2f}%** | {'✅ Pass' if method[2] >= 80 else '❌ Fail'} |
| **Class** | {cls_stat[0]:,} | {cls_stat[1]:,} | **{cls_stat[2]:.2f}%** | {'✅ Pass' if cls_stat[2] >= 80 else '❌ Fail'} |

> **Coverage Gate:** Verified against `minimum = 0.80` across all metrics via `jacocoTestCoverageVerification`.
"""

full_summary_md = test_summary_md + "---\n\n" + coverage_md

step_summary = os.environ.get('GITHUB_STEP_SUMMARY')
if step_summary:
    with open(step_summary, 'a', encoding='utf-8') as f:
        f.write(full_summary_md)

print(full_summary_md)
