---
agent: agent
description: Perform a comprehensive code review of outstanding changes.
---

# Objective

Perform a comprehensive code review of the current outstanding changes (uncommitted or staged).

## Instructions

1. Review all changed files in the current diff.
2. Evaluate changes against the project conventions defined in `AGENTS.md`.
3. Check for the following categories of issues:

   - **Critical**: Security vulnerabilities, data loss risks, crashes, broken builds.
   - **High**: Logic errors, race conditions, missing error handling at system boundaries, performance regressions (e.g., O(n²) where O(n) is possible).
   - **Medium**: Code style violations, missing visibility modifiers (explicit API modules), deviation from project patterns (interface/impl separation, Metro DI conventions, immutable collections in Compose).
   - **Low**: Naming improvements, documentation gaps, minor readability suggestions.

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
