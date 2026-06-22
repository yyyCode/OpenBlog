# Admin Console — Feishu Blue-White Redesign

**Date:** 2026-06-23
**Status:** Approved

## Summary

Redesign the admin console (`/console`) from teal-accented dashboard to a Feishu blue-white professional aesthetic. Visual-only change — no layout, routing, or functional behavior modified.

## Scope

### In Scope

- Console CSS variables (`--console-*` in blog.css)
- Console layout sidebar (ConsoleLayout.vue)
- Dashboard page (ConsoleDashboardView.vue)
- All console child pages (~15 views — table/list/form styled pages)
- Console global component styles (stat cards, nav items, tables, tags, buttons)

### Out of Scope

- Public blog area (already done)
- Auth pages (already done)
- Any functional/behavioral changes
- Dark mode removal (stays as-is, colors updated)

## Design Tokens Change

### `--console-*` Variables

```css
/* Before */
--console-bg: #f0f2f5;
--console-sidebar: #ffffff;
--console-text: #1f2937;
--console-muted: #6b7280;
--console-border: rgba(15, 23, 42, 0.08);
--console-accent: #0d9488;
--console-accent-soft: rgba(13, 148, 136, 0.12);
--console-blue: #2563eb;

/* After */
--console-bg: #f5f6f7;
--console-sidebar: #ffffff;
--console-text: #1f2329;
--console-muted: #8f959e;
--console-border: #e5e6e8;
--console-accent: #3370ff;
--console-accent-soft: rgba(51, 112, 255, 0.08);
--console-blue: #3370ff;
```

Dark mode:
```css
/* Before */
--console-bg: #0f1114;
--console-sidebar: #16181d;
--console-text: #e5e7eb;
--console-muted: #9ca3af;
--console-border: rgba(255, 255, 255, 0.08);
--console-accent-soft: rgba(45, 212, 191, 0.15);

/* After */
--console-bg: #0f1114;
--console-sidebar: #16181d;
--console-text: #e5e7eb;
--console-muted: #9ca3af;
--console-border: #2a2a2e;
--console-accent-soft: rgba(51, 112, 255, 0.12);
```

## Component-Level Changes

### 1. Sidebar (ConsoleLayout.vue + CSS)

| Element | Before | After |
|---------|--------|-------|
| Brand color | `--console-blue` (#2563eb) | `var(--console-text)` (#1f2329) |
| Brand weight | 800 | 700 |
| Brand size | 22px | 18px |
| Search border | `1px solid var(--console-border)` | `1px solid #dee0e3` |
| Search border-radius | 10px | 6px |
| Search bg | `var(--console-bg)` | `#f5f6f7` |
| Nav group title size | 11px | 11px (unchanged) |
| Nav item border-radius | 10px | 6px |
| Nav item weight | 600 | active: 500, inactive: 400 |
| Nav item active bg | `var(--console-bg)` | `rgba(51, 112, 255, 0.06)` |
| Nav item active border | `1px solid var(--console-border)` | none (only bg tint) |
| Nav item active color | `var(--console-text)` | `#3370ff` |
| Nav item icon active | `var(--console-accent)` | `#3370ff` |
| Sidebar border | `1px solid var(--console-border)` | `1px solid #e5e6e8` |

### 2. Dashboard (ConsoleDashboardView.vue + CSS)

| Element | Before | After |
|---------|--------|-------|
| Stat card border-radius | 14px | 8px |
| Stat card border | `1px solid var(--console-border)` | `1px solid #e5e6e8` |
| Stat card shadow | `0 1px 2px rgba(15,23,42,0.04)` | `0 1px 3px rgba(0,0,0,0.04)` |
| Stat icon color | `var(--console-accent)` (#0d9488) | inherit (no forced accent) |
| Stat icon size | default | 18px |
| Stat value size | 26px | 26px (unchanged) |
| Stat value weight | 800 | 700 |
| Quick tile border-radius | 12px | 8px |
| Quick tile bg | `var(--console-bg)` | `#fff` |
| Quick tile border | `1px solid var(--console-border)` | `1px solid #e5e6e8` |
| Quick tile hover accent | `rgba(13, 148, 136, 0.35)` | `#3370ff` |
| Quick icon bg | none | `rgba(51, 112, 255, 0.08)` with border-radius 6px |

### 3. Global Console Elements

| Element | Before | After |
|---------|--------|-------|
| `.console-card` bg | `#fff` | `#fff` (unchanged) |
| `.console-card` border | `1px solid var(--console-border)` | `1px solid #e5e6e8` |
| `.console-card` border-radius | 14px | 8px |
| `.console-card` shadow | `0 1px 2px rgba(15,23,42,0.04)` | `0 1px 3px rgba(0,0,0,0.04)` |
| `.console-btn-dark` bg | `#111827` | `#3370ff` |
| `.console-btn-ghost` bg | `#fff` | `#fff` |
| `.console-btn-ghost` border | `1px solid var(--console-border)` | `1px solid #dee0e3` |
| `.console-page-title h1` weight | 800 | 700 |
| `.console-stat-label` weight | 600 | 500 |
| `.console-search-input` border-radius | 10px | 6px |
| `.console-search-input` bg | `var(--console-bg)` | `#f5f6f7` |
| Table header border-bottom | `2px solid var(--border, #e5e7eb)` | `1px solid #e5e6e8` |
| Table cell border-bottom | `1px solid var(--border, #f3f4f6)` | `1px solid #e5e6e8` |
| `.admin-user-row` border-radius | 12px | 8px |

### 4. Buttons (console-scoped)

| Element | Before | After |
|---------|--------|-------|
| `.console-btn-dark` border-radius | 10px | 6px |
| `.console-btn-dark` font-weight | 700 | 600 |
| `.console-btn-ghost` border-radius | 10px | 6px |
| `.btn-outline-danger` border-radius | inherit | 6px |
| `.btn-outline-warning` border-radius | inherit | 6px |
| `.btn-outline-success` border-radius | inherit | 6px |
| `.btn-sm` border-radius | inherit | 6px |

### 5. Tags/Badges

| Element | Before | After |
|---------|--------|-------|
| `.admin-tag` border-radius | 10px | 6px |
| `.admin-tag` padding | `2px 10px` | `2px 8px` |
| Tag colors (role/status) | semantic colors preserved | unchanged |

## Files to Change

| File | Action |
|------|--------|
| `vue/src/assets/blog.css` | Update `--console-*` variables + all `.console-*` and `.admin-*` CSS classes |
| `vue/src/layouts/ConsoleLayout.vue` | Minor template tweaks if any inline colors |

All ~15 console views should pick up the new styles automatically via CSS variable inheritance and class updates.

## Tech Stack

- Vue 3 Composition API
- Plain CSS (no UI framework)
- No new dependencies
