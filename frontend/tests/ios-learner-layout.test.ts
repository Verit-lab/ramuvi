import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const shell = readFileSync("components/study/StudyShell.tsx", "utf8");
const journeys = readFileSync("app/learn/journeys/page.tsx", "utf8");
const profile = readFileSync("app/learn/profile/page.tsx", "utf8");
const controls = readFileSync("components/student/StudentAccessibilityControls.tsx", "utf8");
const reader = readFileSync("components/study/JourneyContentReader.tsx", "utf8");
const css = readFileSync("components/study/study.module.css", "utf8");
const controlCss = readFileSync("components/student/student-accessibility.module.css", "utf8");

test("iPhone student menu has a single way to Configurações", () => {
  assert.doesNotMatch(shell, /className=\{.*styles\.mobileProfile/);
  assert.match(shell, /id="student-mobile-navigation"/);
  assert.match(shell, /href="\/learn\/profile" aria-current=\{profileActive/);
});

test("Journey filters are mobile-friendly and absent for a truly empty catalog", () => {
  assert.match(journeys, /journeys\.length > 0 && <div className=\{styles\.libraryFilters\}/);
  assert.match(journeys, /type="search"/);
  assert.match(journeys, /className=\{styles\.librarySelect\}/);
  assert.match(journeys, /aria-expanded=\{redeemOpen\}/);
  assert.match(css, /\.libraryFilters \.libraryFilterField input,[\s\S]*min-height:48px;font-size:1rem/);
  assert.match(css, /\.redeemControls input\{[^}]*font-size:1rem/);
});

test("Reader retains native semantic table with independently scrollable and keyboard reachable overflow", () => {
  assert.match(reader, /table: \(\{ children \}\) => <div className=\{styles\.readerTableFrame\}/);
  assert.match(reader, /className=\{styles\.readerTableScroll\} role="region"/);
  assert.match(reader, /tabIndex=\{0\}/);
  assert.match(reader, /<table>\{children\}<\/table>/);
  assert.match(css, /\.readerMarkdown \.readerTableScroll\{[^}]*overflow-x:auto/);
  assert.match(css, /\.readerMarkdown \.readerTableScroll th,[^\n]*\.readerMarkdown \.readerTableScroll td\{[^}]*overflow-wrap:normal;word-break:normal/);
});

test("Settings separates appearance controls with mobile grouping and 48px targets", () => {
  assert.match(profile, /<StudentAccessibilityControls settingsPage \/>/);
  assert.match(controls, /Tamanho do conteúdo/);
  assert.match(controlCss, /\.controls\.settings\{[^}]*grid-template-columns/);
  assert.match(controlCss, /@media\(max-width:600px\)/);
  assert.match(controlCss, /\.settings \.field select\{[^}]*min-height:48px/);
});
