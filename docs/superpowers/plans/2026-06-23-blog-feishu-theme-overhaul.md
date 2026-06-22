# Blog Feishu Theme Overhaul — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul the public blog CSS from purple glassmorphism to Feishu blue-white solid-surface professional aesthetic. Visual-only — no layout or behavior changes.

**Architecture:** All changes are in `blog.css` plus one Vue component (`WelcomeGate.vue`). Tasks are ordered by dependency: tokens first, then global classes, then component sections, then Vue fixups, then build verify.

**Tech Stack:** CSS, Vue 3, Vite

---

## File Structure

| File | Action | Lines affected |
|------|--------|---------------|
| `vue/src/assets/blog.css` | Modify | ~200 lines across multiple sections |
| `vue/src/components/WelcomeGate.vue` | Modify | ~5 lines (gradient colors) |
| `vue/src/assets/style.css` | No change | N/A (already overridden) |

---

### Task 1: Update CSS design tokens (root variables)

**Files:**
- Modify: `vue/src/assets/blog.css:1-34`

- [ ] **Step 1: Replace `:root` variables block**

In `vue/src/assets/blog.css`, replace lines 1-18 (the `:root` block):

```css
:root {
  --container: 1120px;
  --page-pad: 26px;
  --bg: #f5f6f7;
  --text: #1f2329;
  --muted: #8f959e;
  --border: #e5e6e8;
  --accent: #3370ff;
  --accent2: #2860e0;
  --surface: #ffffff;
  --surface-soft: #f5f6f7;
  --placeholder-tint: #e5e6e8;

  /* 纯色背景（不再使用背景图） */
  --page-bg-image: none;
  --page-bg-scrim: transparent;
  --card: #ffffff;
}
```

- [ ] **Step 2: Replace `[data-theme='dark']` variables block**

In `vue/src/assets/blog.css`, replace lines 20-34 (the `[data-theme='dark']` block):

```css
[data-theme='dark'] {
  --bg: #0f1114;
  --text: #e5e7eb;
  --muted: #9ca3af;
  --border: #2a2a2e;
  --accent: #4a7fff;
  --accent2: #3370ff;
  --surface: #1a1a1a;
  --surface-soft: #16181d;
  --placeholder-tint: #2a2a2e;
  --code-bg: #1a1a1a;
  --text-h: #e5e7eb;
  --page-bg-scrim: rgba(12, 12, 14, 0.38);
  --card: #1a1a1a;
}
```

- [ ] **Step 3: Commit**

```bash
git add vue/src/assets/blog.css
git commit -m "style: update root CSS variables to Feishu blue theme"
```

---

### Task 2: Remove glassmorphism and update global base classes

**Files:**
- Modify: `vue/src/assets/blog.css` (~15 locations)

- [ ] **Step 1: Remove all backdrop-filter declarations from blog.css**

Search for all `backdrop-filter:` and `-webkit-backdrop-filter:` lines in the file and remove each entire declaration. The affected classes are:

```
.card                                    → remove backdrop-filter + -webkit-backdrop-filter
.search-input, .icon-link, .btn:not(.primary), .input, .textarea, .tab, .avatar-preview, .article-list-item, .cover-preview, .article-list, .article-editor → remove both lines
.theme-toggle, .back-to-top-btn          → remove both lines
.right-dock                              → remove both lines
.articles-drawer, .widgets-drawer        → remove both lines
```

- [ ] **Step 2: Update `.card` class**

Replace the `.card` rule:

```css
.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  color: var(--text);
}
```

Also update the dark mode `.card` shadow at the bottom of the file:
```css
[data-theme='dark'] .card {
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
}
```

- [ ] **Step 3: Update `.btn` and `.btn.primary` base classes**

```css
.btn {
  border: 1px solid var(--border);
  background: #ffffff;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  font-weight: 600;
  color: var(--text);
}

.btn.primary {
  border-color: transparent;
  background: #3370ff;
  color: #fff;
}
```

- [ ] **Step 4: Update `.input`, `.textarea`, `.search-input` border-radius and focus colors**

Update `.input`, `.textarea`:
```css
.input {
  width: 100%;
  border: 1px solid var(--border);
  background: var(--surface);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
  outline: none;
  color: var(--text);
}

.textarea {
  width: 100%;
  border: 1px solid var(--border);
  background: var(--surface);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
  outline: none;
  resize: vertical;
  color: var(--text);
}

/* Remove the old backdrop-filter group */
.search-input,
.icon-link,
.btn:not(.primary),
.input,
.textarea,
.tab,
.avatar-preview,
.article-list-item,
.cover-preview,
.article-list,
.article-editor {
  /* backdrop-filter and -webkit-backdrop-filter removed */
}
```

Update `.input:focus`, `.textarea:focus`:
```css
.input:focus,
.textarea:focus {
  border-color: #3370ff;
  box-shadow: 0 0 0 3px rgba(51, 112, 255, 0.12);
}
```

Update `.search-input:focus`:
```css
.search-input:focus {
  border-color: #3370ff;
  box-shadow: 0 0 0 3px rgba(51, 112, 255, 0.12);
}
```

- [ ] **Step 5: Update `.tab` classes**

```css
.tab {
  border: 1px solid var(--border);
  background: var(--surface);
  border-radius: 8px;
  padding: 10px 14px;
  cursor: pointer;
  font-weight: 900;
  color: var(--muted);
}

.tab.active {
  border-color: #3370ff;
  background: rgba(51, 112, 255, 0.08);
  color: var(--text);
}
```

- [ ] **Step 6: Update `.sidebar-item.active`**

```css
.sidebar-item.active {
  border-color: #3370ff;
  box-shadow: 0 10px 22px rgba(51, 112, 255, 0.14);
  background: rgba(51, 112, 255, 0.06);
}
```

- [ ] **Step 7: Update comment reply left border**

```css
.comment-replies {
  border-left: 2px solid rgba(51, 112, 255, 0.18);
}
```

- [ ] **Step 8: Commit**

```bash
git add vue/src/assets/blog.css
git commit -m "style: remove glassmorphism and update global classes to Feishu blue"
```

---

### Task 3: Update component-specific CSS sections

**Files:**
- Modify: `vue/src/assets/blog.css` (home page, nav, article, profile, newsletter sections)

- [ ] **Step 1: Update navigation bar styles**

Update `.site-top-bar` to use solid border:
```css
.site-top-bar {
  /* ... existing padding/position ... */
  background: #ffffff;
  border-bottom: 1px solid var(--border);
}
```

Update nav link active style — change from `::after` bar to Feishu underline:
```css
.site-nav-link.active {
  color: var(--accent);
  position: relative;
}

.site-nav-link.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -12px;
  height: 2px;
  background: var(--accent);
  opacity: 1;
}
```

Update nav auth pills:
```css
.site-nav-pill-outline {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 6px 16px;
  color: var(--muted);
  text-decoration: none;
}

.site-nav-pill-solid {
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 6px 20px;
  background: #3370ff;
  color: #fff;
  font-weight: 500;
  text-decoration: none;
}
```

Update dark mode pill:
```css
[data-theme='dark'] .site-nav-pill-solid {
  border-color: transparent;
  background: #3370ff;
  color: #fff;
}
```

- [ ] **Step 2: Update home page styles**

Update hero:
```css
.home-hero-title {
  margin: 0;
  font-size: 42px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: var(--text);
}

.home-hero-sub {
  margin: 12px 0 0;
  font-size: 16px;
  line-height: 1.6;
  color: var(--muted);
}

@media (max-width: 1024px) {
  .home-hero-title { font-size: 36px; }
  .home-hero-sub { font-size: 15px; }
}
@media (max-width: 680px) {
  .home-hero { padding-top: 34px; }
  .home-hero-title { font-size: 28px; }
  .home-hero-sub { font-size: 14px; }
}
```

Update home post cards:
```css
.home-post-card {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: #ffffff;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

[data-theme='dark'] .home-post-card {
  background: var(--surface);
}

.home-post-card:hover {
  border-color: var(--accent);
  box-shadow: 0 4px 16px rgba(51, 112, 255, 0.1);
}

.home-post-title {
  margin-top: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text);
  line-height: 1.3;
}
```

Update featured article:
```css
.home-featured-grid:hover .home-featured-cover img {
  transform: scale(1.03);
}

.home-featured-cover {
  border-radius: 10px;
  overflow: hidden;
  background: var(--placeholder-tint);
  aspect-ratio: 4 / 3;
}

.home-featured-title {
  margin-top: 12px;
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--text);
  line-height: 1.2;
}
```

Update newsletter:
```css
.home-newsletter {
  margin-top: 96px;
  border-radius: 10px;
  background: var(--surface-soft);
  border: 1px solid var(--border);
}

.home-newsletter-btn {
  border: 1px solid transparent;
  background: #3370ff;
  color: #fff;
  border-radius: 8px;
  padding: 14px 24px;
  cursor: pointer;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

[data-theme='dark'] .home-newsletter-btn {
  background: #3370ff;
  color: #fff;
}
```

- [ ] **Step 3: Update article page styles**

```css
.article-reader-title {
  margin: 0 0 18px;
  font-size: clamp(28px, 4.2vw, 36px);
  font-weight: 700;
  letter-spacing: -0.025em;
  line-height: 1.15;
  color: var(--text);
}

.article-reader-cover {
  margin: 0 0 44px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--placeholder-tint);
}
```

- [ ] **Step 4: Update profile card and blog info card**

Update profile menu items (dark pills → blue):
```css
.profile-menu-item {
  text-decoration: none;
  font-weight: 700;
  font-size: 13px;
  color: #fff;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: #3370ff;
  transition: background 0.15s ease;
}

.profile-menu-item:hover {
  background: #2860e0;
}

[data-theme='dark'] .profile-menu-item {
  background: #3370ff;
}
[data-theme='dark'] .profile-menu-item:hover {
  background: #4a7fff;
}
```

Update icon-link:
```css
.icon-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 8px;
  padding: 8px 12px;
  border: 1px solid transparent;
  text-decoration: none;
  font-weight: 700;
  color: #fff;
  background: #3370ff;
  transition: background 0.15s ease;
}

.icon-link:hover {
  background: #2860e0;
}

[data-theme='dark'] .icon-link {
  background: #3370ff;
}
[data-theme='dark'] .icon-link:hover {
  background: #4a7fff;
}
```

Update profile name weight:
```css
.profile-name {
  font-weight: 700;
  font-size: 20px;
  margin-top: 10px;
  color: var(--text);
}
```

- [ ] **Step 5: Update right dock**

```css
.right-dock {
  position: fixed;
  right: max(14px, env(safe-area-inset-right, 0px));
  top: 86px;
  transform: none;
  z-index: 9996;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--surface);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: visible;
}
```

- [ ] **Step 6: Update comment section**

```css
.comment-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--border);
  flex-shrink: 0;
  background: var(--surface);
}
```

- [ ] **Step 7: Remove `--accent` purple from `#next-steps` / old template styles in style.css (if any)**

No action needed — `style.css` uses `var(--accent)` which now resolves to `#3370ff` automatically.

- [ ] **Step 8: Commit**

```bash
git add vue/src/assets/blog.css
git commit -m "style: update component CSS sections to Feishu blue theme"
```

---

### Task 4: Update Vue component scoped styles

**Files:**
- Modify: `vue/src/components/WelcomeGate.vue` (scoped style block, ~3 lines)

- [ ] **Step 1: Update WelcomeGate.vue gradient colors**

In `vue/src/components/WelcomeGate.vue`, update the `.welcome-illust-bg` gradient:

```css
/* Before */
.welcome-illust-bg {
  background: linear-gradient(135deg, #f0e6ff 0%, #e6f0ff 40%, #f5e6ff 70%, #e6f5ff 100%);
}

/* After */
.welcome-illust-bg {
  background: linear-gradient(135deg, #e6f0ff 0%, #f0f5ff 40%, #e6eeff 70%, #f0f5ff 100%);
}
```

Update dark mode:
```css
/* Before */
[data-theme='dark'] .welcome-illust-bg {
  background: linear-gradient(135deg, #2a2040 0%, #1a2a40 40%, #2a1a40 70%, #1a2a45 100%);
}

/* After */
[data-theme='dark'] .welcome-illust-bg {
  background: linear-gradient(135deg, #1a2a3a 0%, #1a2435 40%, #1a2a3a 70%, #1a2435 100%);
}
```

Update the SVG illustration color:
```css
/* Before */
.welcome-illust-svg {
  color: #7c3aed;
}

/* After */
.welcome-illust-svg {
  color: #3370ff;
}

/* Before */
[data-theme='dark'] .welcome-illust-svg {
  color: #a78bfa;
}

/* After */
[data-theme='dark'] .welcome-illust-svg {
  color: #4a7fff;
}
```

Update the enter button hover shadow:
```css
/* Before */
.welcome-enter:hover {
  box-shadow: 0 8px 24px rgba(170, 59, 255, 0.35);
}

/* After */
.welcome-enter:hover {
  box-shadow: 0 8px 24px rgba(51, 112, 255, 0.35);
}
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/components/WelcomeGate.vue
git commit -m "style: update WelcomeGate gradient to Feishu blue"
```

---

### Task 5: Build verification

- [ ] **Step 1: Run Vite build**

```bash
cd vue && npm run build 2>&1 | tail -10
```

Expected: `✓ built in X.XXs` — no errors.

- [ ] **Step 2: Fix any build issues and commit**

Only if the build reveals issues. Then:

```bash
git add -A
git commit -m "chore: fix build issues from theme overhaul"
```
