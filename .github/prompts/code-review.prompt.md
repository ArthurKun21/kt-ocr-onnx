---
agent: agent
description: Perform a comprehensive code review of outstanding changes.
---

# Objective

Perform a comprehensive code review of the current outstanding changes (uncommitted or staged).

## Instructions

1. Review all changed files in the current diff.
2. Evaluate changes against the project conventions defined in `AGENTS.md`, especially:
   - Kotlin `explicitApi()` expectations: public APIs need explicit visibility and non-API code should stay `internal`.
   - Kotlin Multiplatform boundaries: keep `expect`/`actual` and source-set-specific code in the correct source set.
   - Style rules: 4-space indentation, no wildcard imports, and a maximum line length of 120.
   - Public API changes: note when ABI-related updates or missing API coverage should be called out.
   - Existing architecture patterns around `OcrApi`, `PaddleOcrService`, and recognition model abstractions.
3. Check for the following categories of issues:

   - **Critical**: Security vulnerabilities, data loss risks, crashes, or broken builds.
   - **High**: Logic errors, race conditions, missing boundary error handling, or clear performance regressions.
   - **Medium**: Violations of the project conventions listed above.
   - **Low**: Naming, documentation, or minor readability improvements.

   Use the highest applicable severity for each finding.

4. For each issue found, identify:
   - **Location**: file path and line number.
   - **Problem**: what is wrong.
   - **Why**: why it matters.
   - **Fix**: a concrete suggestion to resolve it.

## Output

Present results in this exact format:

### Code Review Results

**X issues found across Y checks**

| # | Severity | Category | Location | Problem | Why | Fix |
|---|----------|----------|----------|---------|-----|-----|
| 1 | CRITICAL | security | file:line | problem | why | fix |
| 2 | HIGH | logic | file:line | problem | why | fix |
| 3 | MEDIUM | style | file:line | problem | why | fix |
| 4 | LOW | naming | file:line | problem | why | fix |

**Checks performed:** list each check category and what patterns were inspected.

Then ask: "Would you like me to fix any of these issues? (e.g., 'fix issue #1' or 'fix issues #2 and #3')".
