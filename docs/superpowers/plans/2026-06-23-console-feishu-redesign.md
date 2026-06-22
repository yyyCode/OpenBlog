# Console Feishu Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign admin console CSS from teal-accented to Feishu blue-white aesthetic. Visual-only — no functionality or layout changes.

**Architecture:** All changes in `blog.css` console section (lines ~2572-3542). The `--console-*` CSS variables drive most components via `var()` references. ConsoleLayout.vue checked for inline styles.

**Tech Stack:** CSS, Vue 3

---

## File Structure

| File | Action |
|------|--------|
| `vue/src/assets/blog.css` | Modify console section (~200 lines) |
| `vue/src/layouts/ConsoleLayout.vue` | Check for inline styles (likely none) |

---

### Task 1: Update console CSS variables

**Files:**
- Modify: `vue/src/assets/blog.css` (the `--console-*` block at lines ~2572-2605)

- [ ] **Step 1: Replace console `:root` variables**

Find the `.console-root` block in blog.css and replace:

```css
.console-root {
  --console-bg: #f5f6f7;
  --console-sidebar: #ffffff;
  --console-text: #1f2329;
  --console-muted: #8f959e;
  --console-border: #e5e6e8;
  --console-accent: #3370ff;
  --console-accent-soft: rgba(51, 112, 255, 0.08);
  --console-blue: #3370ff;
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: stretch;
  background: var(--console-bg);
  color: var(--console-text);
  font-family:
    ui-sans-serif,
    system-ui,
    -apple-system,
    'Segoe UI',
    Roboto,
    'PingFang SC',
    'Microsoft YaHei',
    sans-serif;
}
```

- [ ] **Step 2: Replace console dark mode variables**

```css
[data-theme='dark'] .console-root {
  --console-bg: #0f1114;
  --console-sidebar: #16181d;
  --console-text: #e5e7eb;
  --console-muted: #9ca3af;
  --console-border: #2a2a2e;
  --console-accent: #4a7fff;
  --console-accent-soft: rgba(51, 112, 255, 0.12);
}
```

- [ ] **Step 3: Commit**

```bash
git add vue/src/assets/blog.css
git commit -m "style: update console CSS variables to Feishu blue"
```

---

### Task 2: Update console component CSS classes

**Files:**
- Modify: `vue/src/assets/blog.css` (all `.console-*` and `.admin-*` classes)

- [ ] **Step 1: Update sidebar styles**

Update sidebar, brand, search, nav items:

```css
.console-sidebar {
  width: 248px;
  flex-shrink: 0;
  background: var(--console-sidebar);
  border-right: 1px solid var(--console-border);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.console-brand {
  display: block;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--console-text);
  text-decoration: none;
  margin-bottom: 18px;
}

.console-search-input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 72px 8px 34px;
  border-radius: 6px;
  border: 1px solid #dee0e3;
  background: #f5f6f7;
  color: var(--console-text);
  font-size: 13px;
  outline: none;
}

[data-theme='dark'] .console-search-input {
  background: #2a2a2e;
  border-color: #3a3a3e;
}

.console-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  color: var(--console-text);
  text-decoration: none;
  font-size: 14px;
  font-weight: 400;
  margin-bottom: 2px;
  border: 1px solid transparent;
  transition: background 0.15s ease, color 0.15s ease;
}

.console-nav-item:hover {
  background: var(--console-bg);
}

.console-nav-item.active {
  background: rgba(51, 112, 255, 0.06);
  color: #3370ff;
  border-color: transparent;
  font-weight: 500;
}

.console-nav-item.active .console-nav-ico {
  color: #3370ff;
}

.console-nav-ico {
  display: flex;
  color: var(--console-muted);
  flex-shrink: 0;
}

.console-nav-group-title {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--console-muted);
  margin: 14px 8px 8px;
}
```

- [ ] **Step 2: Update dashboard/stat cards**

```css
.console-stat-card {
  background: #fff;
  border: 1px solid var(--console-border);
  border-radius: 8px;
  padding: 18px 18px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.console-stat-ico {
  color: inherit;
  margin-bottom: 10px;
  font-size: 18px;
}

.console-stat-label {
  font-size: 12px;
  color: var(--console-muted);
  font-weight: 500;
}

.console-stat-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.console-quick-tile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 8px;
  border: 1px solid var(--console-border);
  background: #fff;
  text-decoration: none;
  color: var(--console-text);
  font-weight: 500;
  font-size: 14px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.console-quick-tile:hover {
  border-color: #3370ff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.console-quick-ico {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: rgba(51, 112, 255, 0.08);
  color: #3370ff;
  flex-shrink: 0;
}
```

- [ ] **Step 3: Update global console card/button/table styles**

```css
.console-card {
  background: #fff;
  border: 1px solid var(--console-border);
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.console-btn-dark {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 6px;
  background: #3370ff;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  border: none;
  cursor: pointer;
}

[data-theme='dark'] .console-btn-dark {
  background: #3370ff;
  color: #fff;
}

.console-btn-ghost {
  padding: 8px 18px;
  border-radius: 6px;
  border: 1px solid #dee0e3;
  background: #fff;
  color: var(--console-text);
  font-weight: 600;
  cursor: pointer;
}

.console-page-title h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.console-page-title-ico {
  display: flex;
  color: var(--console-accent);
}
```

- [ ] **Step 4: Update admin table/user list styles**

```css
.admin-user-table th {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--console-border);
  color: var(--console-muted);
  font-weight: 600;
  font-size: 13px;
  white-space: nowrap;
}

.admin-user-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--console-border);
  vertical-align: middle;
}

.admin-user-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid var(--console-border);
  border-radius: 8px;
  background: var(--console-bg);
}

.admin-user-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--console-text);
  text-decoration: none;
  font-weight: 600;
}

.admin-user-link:hover {
  color: #3370ff;
}
```

- [ ] **Step 5: Update tag/badge styles**

```css
.admin-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
```
Keep all tag color variants (`.tag-role-admin`, `.tag-status-active`, etc.) unchanged — their red/blue/green/yellow colors are acceptable semantic colors.

- [ ] **Step 6: Commit**

```bash
git add vue/src/assets/blog.css
git commit -m "style: update console component CSS to Feishu blue theme"
```

---

### Task 3: Check ConsoleLayout.vue + build verify

- [ ] **Step 1: Check ConsoleLayout.vue for inline styles**

Read `vue/src/layouts/ConsoleLayout.vue`. If any inline styles use `#2563eb`, `#0d9488`, or `#111827`, update them to `#3370ff`. If no hardcoded colors exist, skip.

- [ ] **Step 2: Run build**

```bash
cd vue && npm run build 2>&1 | tail -5
```
Expected: `✓ built in X.XXs`

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "chore: verify console build after Feishu redesign"
```
