package app.superhealth.ui.cards

import app.superhealth.platform.*

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import app.superhealth.cards.CardDef
import app.superhealth.cards.Finish
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Fonds de carte générés : une scène par biome (paysage animé), dont chaque fond allume ses propres éléments et son
 * cadrage ([app.superhealth.cards.Look]), trois couleurs et une graine par carte. Une épique peint deux biomes,
 * séparés par une couture de lumière. Chaque combinaison (biome, cadrage) devient un petit shader à part, compilé une
 * fois puis gardé : un shader unique avec tous les biomes prenait plusieurs secondes à compiler sur le GPU.
 */
private const val ART_HEAD = """
uniform float2 iRes; uniform float iTime; uniform float iSeed; uniform float2 iTilt; uniform float iStyle; uniform float iStyle2; uniform float iRar; uniform float iChroma;
uniform float4 cA; uniform float4 cB; uniform float4 cC; uniform float4 dA; uniform float4 dB; uniform float4 dC;
uniform float4 iF; uniform float4 iF2; uniform float3 iCam; uniform float3 iCam2;

float h21(float2 p) { p = mod(p, 289.0); float3 p3 = fract(float3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float n2(float2 p) { float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + float2(1,0)), u.x), mix(h21(i + float2(0,1)), h21(i + float2(1,1)), u.x), u.y); }
float fbm(float2 p) { float v = 0.0; float a = 0.5; for (int i = 0; i < 4; i++) { v += a * n2(p); p = p * 2.03 + float2(1.7, 9.2); a *= 0.5; } return v; }
float3 pal(float x, float3 A, float3 B, float3 C) { x = clamp(x, 0.0, 1.0); return x < 0.5 ? mix(C, B, x * 2.0) : mix(B, A, x * 2.0 - 1.0); }
float stars(float2 p, float d) { float2 g = floor(p * d); float h = h21(g + iSeed); float2 f = fract(p * d) - 0.5; return h > 0.965 ? smoothstep(0.08, 0.0, length(f)) * (0.5 + 0.5 * sin(iTime * 2.0 + h * 40.0)) : 0.0; }
float2 vor(float2 p) { float2 i = floor(p); float2 f = fract(p); float d1 = 8.0; float d2 = 8.0; float id = 0.0;
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) { float2 g = float2(float(x), float(y)); float2 o = float2(h21(i + g + iSeed), h21(i + g + 7.0 + iSeed));
    o = 0.5 + 0.4 * sin(iTime * 0.3 + 6.28 * o); float d = length(g + o - f); if (d < d1) { d2 = d1; d1 = d; id = h21(i + g); } else if (d < d2) { d2 = d; } }
  return float2(d2 - d1, id); }
// Particules qui dérivent : chaque cellule de la grille garde (ou non, selon keep) une poussière.
float motes(float2 q, float2 vel, float dens, float sz, float keep) {
  float2 g = q * dens + vel * iTime * dens; float2 i = floor(g); float2 f = fract(g) - 0.5; float h = h21(i + iSeed * 3.1);
  float2 o = float2(h21(i + 1.3), h21(i + 4.7)) - 0.5;
  return step(keep, h) * smoothstep(sz * dens * 0.5, 0.0, length(f - o * 0.6)) * (0.6 + 0.4 * sin(iTime * 3.0 + h * 30.0)); }
// Crêtes de montagne (bruit en arêtes) et lignes de sapins.
float peaks(float x, float k) { float v = 0.0; float a = 0.5; float f = 1.0; for (int i = 0; i < 4; i++) { v += a * (1.0 - abs(n2(float2(x * f + k, k)) * 2.0 - 1.0)); f *= 2.1; a *= 0.5; } return v; }
float pines(float x, float k, float fr) { float g = x * fr + k; float i = floor(g); float f = fract(g); float h = 0.45 + 0.55 * h21(float2(i, k)); return h * (1.0 - abs(f * 2.0 - 1.0)); }
float bolt(float2 q, float x0, float k, float t) { float x = x0 + 0.14 * (fbm(float2(q.y * 4.0, floor(t * 2.0) + k * 9.0)) - 0.5) * 2.0; return abs(q.x - x); }

"""
private val ART_BIOMES = arrayOf("""// Chaque scène reçoit ses éléments de paysage F (0 ou 1) et sa ligne d'horizon hz ; F.x est toujours le focal du biome.
// Magma : x volcan en éruption, y rivière de lave, z tempête de braises et cendres, w aiguilles d'obsidienne.
float3 magma(float2 q, float3 A, float3 B, float3 C, float4 F, float hz) {
  float t = iTime; float s = iSeed;
  float3 col = mix(B * 0.45 + C * 0.6, C, smoothstep(hz - 0.05, hz + 0.55, q.y));
  col += B * (0.2 + 0.25 * F.z) * fbm(q * 2.5 + float2(s, -t * 0.08)) * smoothstep(hz, hz + 0.5, q.y);
  float g = hz + 0.025 * sin(q.x * 7.0 + s);
  if (F.x > 0.5) {
    float2 pp = q - float2(0.04 * sin(s), hz + 0.26);
    float plume = fbm(float2(pp.x * 6.0, pp.y * 3.0 - t * 0.6) + s) * smoothstep(0.3, 0.0, abs(pp.x) - pp.y * 0.6) * step(0.0, pp.y);
    col += mix(B, A, plume) * plume * 1.3 + A * exp(-length(pp) * 9.0) * 0.8;
    float cone = hz + 0.27 - abs(q.x - pp.x * 0.0 - 0.04 * sin(s)) * 0.85 + 0.012 * (n2(q * 25.0 + s) - 0.5);
    if (q.y < cone && q.y > g - 0.02) {
      col = C * 0.45 + B * 0.06 * n2(q * 30.0);
      float flow = smoothstep(0.012, 0.0, abs(q.x - 0.04 * sin(s) - 0.05 * sin(q.y * 16.0 + s)) - 0.003 - (cone - q.y) * 0.04);
      col += mix(B, A, 0.5 + 0.5 * sin(q.y * 40.0 + t * 3.0)) * flow * (0.6 + 0.4 * fbm(q * 20.0 - t));
      col += B * smoothstep(0.015, 0.0, cone - q.y) * 0.7;
    }
  }
  if (q.y < g) {
    float2 v = vor(float2(q.x * 5.0, q.y * 7.0) + s);
    float crack = smoothstep(0.09, 0.0, v.x); float pulse = 0.65 + 0.35 * sin(t * 1.6 + v.y * 6.28);
    col = mix(C * 0.5, C * 1.2 + B * 0.06, v.y); col = mix(col, B, crack * pulse); col += A * pow(crack, 4.0) * pulse;
    if (F.y > 0.5) {
      float rv = abs(q.y - (g - 0.2) + 0.06 * sin(q.x * 6.0 + s) + 0.03 * fbm(q * 4.0));
      col = mix(col, mix(B, A, fbm(float2(q.x * 8.0 - t * 0.4, q.y * 12.0))), smoothstep(0.06, 0.035, rv));
      col += B * 0.4 * exp(-rv * 12.0);
    }
  }
  if (F.w > 0.5) for (int k = 0; k < 3; k++) { float fk = float(k);
    float cx = (fk - 1.0) * 0.26 + 0.05 * sin(s + fk); float hgt = 0.16 + 0.08 * h21(float2(fk, s));
    float d = abs(q.x - cx) * (2.6 + fk) - (g - 0.05 + hgt - q.y);
    if (d < 0.0 && q.y > g - 0.2) { col = mix(C * 0.25, B * 0.3, step(cx, q.x)) ; col += A * 0.5 * smoothstep(-0.008, 0.0, d); }
  }
  col += mix(B, A, 0.6) * motes(q, float2(0.02 + 0.06 * F.z, 0.18), 22.0, 0.012, 0.84 - 0.12 * F.z) * 1.4;
  return col;
}

""", """// Glacier : x grand cristal, y aurore, z blizzard, w lac gelé avec reflets.
float3 glacier(float2 q, float3 A, float3 B, float3 C, float4 F, float hz) {
  float t = iTime; float s = iSeed;
  float3 col = mix(B * 0.6 + C * 0.5, C, smoothstep(hz, hz + 0.6, q.y));
  col += stars(q, 30.0) * smoothstep(hz + 0.2, hz + 0.5, q.y);
  if (F.y > 0.5) for (int k = 0; k < 2; k++) { float fk = float(k);
    float y = hz + 0.36 - fk * 0.08 + 0.05 * sin(q.x * (3.0 + fk) + t * 0.4 + s + fbm(q * 2.0 + fk) * 2.0);
    col += mix(A, B, fk) * exp(-abs(q.y - y) * 13.0) * 0.6 * (0.6 + 0.4 * fbm(float2(q.x * 6.0, t * 0.2 + fk))); }
  float sp = hz + 0.08 + 0.3 * pow(peaks(q.x * 1.4, s), 2.2);
  if (q.y < sp) { col = mix(B * 0.7 + A * 0.25, A * 0.9, smoothstep(sp - 0.15, sp, q.y) * 0.7); col += A * 0.3 * smoothstep(0.004, 0.0, sp - q.y); }
  float sp2 = hz - 0.04 + 0.18 * pow(peaks(q.x * 2.3 + 4.0, s + 2.0), 2.2);
  if (q.y < sp2) { col = mix(C * 0.7 + B * 0.35, B * 0.9, smoothstep(sp2 - 0.2, sp2, q.y)); col += A * 0.45 * smoothstep(0.006, 0.0, sp2 - q.y); }
  if (F.x > 0.5) {
    float2 c = q - float2(0.03 * sin(s), hz + 0.12); float d = abs(c.x) * 2.6 + abs(c.y) * 1.1;
    if (d < 0.28) { float face = step(0.0, c.x) * 0.25 + step(0.0, c.y) * 0.2; col = mix(B, A, 0.35 + face + 0.3 * (1.0 - d / 0.28)); col += A * smoothstep(0.01, 0.0, abs(c.x)) * 0.5; }
    col += A * exp(-d * 6.0) * 0.5 * (0.8 + 0.2 * sin(t * 2.0));
  }
  float fg = hz - 0.19 + 0.03 * sin(q.x * 5.0 + s);
  if (q.y < fg) {
    if (F.w > 0.5) {
      // Lac gelé : la montagne s'y reflète, striée de fissures.
      float ry = 2.0 * fg - q.y; float rsp = hz + 0.08 + 0.3 * pow(peaks(q.x * 1.4, s), 2.2);
      col = mix(C * 0.8 + B * 0.2, B * 0.6 + A * 0.2, step(ry, rsp) * 0.6 + 0.2) * 0.8;
      col += A * 0.5 * smoothstep(0.03, 0.0, abs(fract(q.x * 3.0 + n2(q * 8.0) * 0.6 + s) - 0.5) - 0.48);
      col += A * 0.15 * pow(1.0 - (fg - q.y) * 2.0, 4.0);
    } else { float2 v = vor(q * 6.0 + s); col = pal(0.2 + 0.6 * v.y, A, B, C); col = mix(col, A, smoothstep(0.05, 0.0, v.x)); }
  }
  if (F.z > 0.5) { col = mix(col, A * 0.8 + B * 0.2, 0.25 * fbm(float2(q.x * 3.0 + t * 0.6, q.y * 2.0))); col += A * motes(q, float2(0.35, -0.2), 30.0, 0.01, 0.45); }
  else col += A * motes(q, float2(0.03, -0.1), 26.0, 0.008, 0.62);
  return col;
}

""", """// Tempête : x œil du cyclone, y éclairs multiples, z pluie battante, w mer déchaînée (au lieu de la plaine).
float3 storm(float2 q, float3 A, float3 B, float3 C, float4 F, float hz) {
  float t = iTime; float s = iSeed;
  float flash = 0.0;
  float3 col = mix(C * 1.3 + B * 0.08, C * 0.8, smoothstep(hz, hz + 0.7, q.y));
  float cl = fbm(float2(q.x * 2.2 + t * 0.06, q.y * 3.2) + s + fbm(q * 2.0 - t * 0.04));
  col = mix(col, mix(C * 1.6 + B * 0.1, B * 0.45, cl), smoothstep(hz + 0.15, hz + 0.55, q.y) * 0.9);
  if (F.x > 0.5) { float2 c = q - float2(0.05 * sin(s), hz + 0.45); float rr = length(c); float a = atan(c.y, c.x);
    float sp = sin(a * 3.0 + rr * 26.0 - t * 1.6 + s); col += B * smoothstep(0.6, 1.0, sp) * exp(-rr * 4.0) * 0.8; col = mix(col, C * 0.4 + A * 0.05, smoothstep(0.06, 0.0, rr)); }
  float g = hz + 0.03 * n2(float2(q.x * 3.0, s));
  for (int k = 0; k < 3; k++) { float fk = float(k);
    if (fk < 0.5 + F.y * 2.0) {
      float flick = step(0.72, fract(t * (0.45 + fk * 0.21) + fk * 0.37 + s * 0.1));
      float d = bolt(q, (fk - 1.0) * 0.18 + 0.05 * sin(s + fk), fk, t * (0.45 + fk * 0.21) + fk * 0.37);
      float on = flick * step(g, q.y) * step(q.y, hz + 0.55);
      col += (A * 0.008 / (d + 0.003) + B * 0.04 / (d + 0.04)) * on; flash = max(flash, flick);
    } }
  col += B * 0.12 * flash * smoothstep(hz - 0.1, hz + 0.7, q.y);
  if (q.y < g) {
    if (F.w > 0.5) {
      float wv = sin(q.x * 18.0 + t * 1.5 + fbm(q * 6.0) * 4.0) * 0.5 + 0.5;
      col = mix(C * 0.4, C * 0.8 + B * 0.25, wv * smoothstep(g - 0.35, g, q.y));
      col += A * 0.5 * smoothstep(0.9, 1.0, wv) * smoothstep(g - 0.3, g, q.y) + B * 0.3 * flash * smoothstep(g - 0.2, g, q.y);
    } else { col = C * 0.35 + B * 0.05 * fbm(q * 8.0); col += B * 0.2 * flash * smoothstep(g - 0.2, g, q.y); }
  }
  float dens = 0.86 - 0.14 * F.z;
  float2 rq = float2((q.x + q.y * (0.25 + 0.2 * F.z)) * 70.0, q.y * 3.0 + t * 5.0); float ho = h21(float2(floor(rq.x), 3.0)) * 9.0;
  float rh = h21(float2(floor(rq.x), floor(rq.y + ho)));
  col += A * (0.22 + 0.1 * F.z) * step(dens, rh) * smoothstep(0.35, 0.0, abs(fract(rq.x) - 0.5)) * smoothstep(0.0, 0.5, fract(rq.y + ho));
  return col;
}

""", """// Montagne : x grand sommet enneigé, y mer de nuages, z soleil (sinon nuit étoilée et lune), w sentier et forêt de sapins.
float3 mountain(float2 q, float3 A, float3 B, float3 C, float4 F, float hz) {
  float t = iTime; float s = iSeed;
  float3 col = mix(A * 0.9 + B * 0.2, mix(B, C, 0.75), smoothstep(hz, hz + 0.6, q.y));
  if (F.z > 0.5) { float2 c = q - float2(0.11, hz + 0.2); float rr = length(c); col = mix(col, A * 1.1, smoothstep(0.065, 0.06, rr)); col += A * exp(-rr * 7.0) * 0.45; }
  else { col = mix(col, C * 0.8, 0.5); col += stars(q, 32.0) * smoothstep(hz + 0.15, hz + 0.45, q.y); float rr = length(q - float2(-0.16, hz + 0.33)); col = mix(col, A, smoothstep(0.04, 0.036, rr) * 0.9); col += A * exp(-rr * 9.0) * 0.25; }
  col += A * 0.12 * fbm(float2(q.x * 3.0 + t * 0.02, q.y * 8.0) + s) * smoothstep(hz + 0.1, hz + 0.4, q.y);
  for (int k = 0; k < 4; k++) { float fk = float(k);
    float base = hz + 0.12 - fk * 0.11; float amp = (F.x > 0.5 && fk < 0.5) ? 0.46 : 0.26 - fk * 0.035;
    float h = base + amp * pow(peaks(q.x * (1.1 + fk * 0.6) + fk * 5.0, s + fk), 1.6);
    if (q.y < h) {
      col = mix(mix(B * 0.65 + A * 0.25, C * 0.45, fk / 3.0), mix(B * 0.8 + A * 0.2, C * 0.7, fk / 3.0), smoothstep(h - 0.12, h, q.y) * step(0.0, q.x - 0.4 * (q.y - h) + 0.02 * n2(q * 20.0)));
      if (fk < 1.5) col = mix(col, A, smoothstep(h - 0.035, h - 0.005, q.y) * smoothstep(base + amp * 0.35, base + amp * 0.7, h) * 0.85);
      if (F.w > 0.5 && fk > 2.5) { float pn = base + 0.06 * pines(q.x, s + 4.0, 16.0); if (q.y > h - 0.06 || q.y < pn) {} col = mix(col, C * 0.3, step(q.y, base + 0.02 + 0.09 * pines(q.x, s + 9.0, 14.0)) * 0.9);
        col += A * 0.5 * smoothstep(0.006, 0.0, abs(q.x - 0.12 * sin(q.y * 9.0 + s) - 0.05) - 0.002) * step(q.y, base); }
    }
    if (F.y > 0.5 && fk > 0.5 && fk < 1.5) {
      float top = base - 0.02 + 0.04 * fbm(float2(q.x * 4.0 + t * 0.03, s));
      if (q.y < top && q.y > top - 0.12) col = mix(col, float3(1.0) * 0.92 + A * 0.08, smoothstep(top - 0.12, top - 0.02, q.y) * (0.75 + 0.25 * fbm(q * 9.0 + float2(t * 0.05, 0.0))));
    }
    col = mix(col, A * 0.75 + B * 0.2, 0.22 * smoothstep(base - 0.02, base - 0.12, q.y) * step(h, q.y));
  }
  col += A * motes(q, float2(0.08, 0.0), 18.0, 0.006, 0.9) * 0.5;
  return col;
}

""")
private val BIOME_FN = arrayOf("magma", "glacier", "storm", "mountain")
private const val ART_MACRO = """// Gros plan de matière : lave craquelée, cristaux givrés, toile d'éclairs, strates de roche.
float3 macro(float st, float2 q, float3 A, float3 B, float3 C) {
  float t = iTime; float s = iSeed;
  if (st < 0.5) { float2 v = vor(q * 4.0 + s); float crack = smoothstep(0.12, 0.0, v.x); float pulse = 0.6 + 0.4 * sin(t * 1.5 + v.y * 6.28);
    float3 col = mix(C * 0.4, C * 1.1 + B * 0.12, v.y); col = mix(col, B, crack * pulse); col += A * pow(crack, 5.0) * pulse * 1.2;
    return col + mix(B, A, 0.5) * motes(q, float2(0.02, 0.15), 18.0, 0.014, 0.8) * 1.4; }
  if (st < 1.5) { float2 v = vor(q * 3.0 + s); float3 col = pal(0.25 + 0.65 * v.y, A, B, C); col = mix(col, A, smoothstep(0.04, 0.0, v.x));
    float fr = abs(sin((q.x + q.y) * 40.0 + fbm(q * 6.0 + s) * 8.0)); col += A * smoothstep(0.96, 1.0, fr) * 0.35 * (1.0 - v.y);
    return col + A * motes(q, float2(0.0, -0.05), 30.0, 0.006, 0.7); }
  if (st < 2.5) { float3 col = C * 0.9 + B * 0.18 * fbm(q * 3.0 + t * 0.1);
    for (int k = 0; k < 4; k++) { float fk = float(k); float a = fk * 0.8 + s; float2 r = float2(cos(a) * q.x - sin(a) * q.y, sin(a) * q.x + cos(a) * q.y);
      float flick = step(0.5, fract(t * (0.3 + fk * 0.13) + fk * 0.29)); float d = bolt(r, 0.0, fk, t * (0.3 + fk * 0.13));
      col += (A * 0.006 / (d + 0.003) + B * 0.03 / (d + 0.03)) * (0.35 + 0.65 * flick); }
    return col; }
  float w = fbm(q * 2.0 + s); float band = sin((q.y + w * 0.35) * 40.0);
  float3 col = mix(C * 0.8, mix(B, A, 0.4 + 0.3 * sin(q.y * 9.0 + s)), 0.5 + 0.5 * band);
  return col * (0.75 + 0.25 * fbm(q * 12.0)) + A * stars(q, 30.0) * 1.2;
}

"""
private const val ART_POSTER = """// Affiche en aplats : ciel en bandes, grand astre cerclé, trois silhouettes du biome avec un liseré de lumière.
float3 poster(float st, float2 q, float3 A, float3 B, float3 C, float hz) {
  float t = iTime; float s = iSeed;
  float k = clamp((q.y - hz + 0.1) / 0.75, 0.0, 1.0); float3 col = mix(mix(A, B, 0.4), C * 1.3, floor(k * 5.0) / 4.0);
  float rr = length(q - float2(0.06 * sin(s), hz + 0.2));
  col = mix(col, A, smoothstep(0.17, 0.165, rr));
  col = mix(col, A * 0.85 + B * 0.15, smoothstep(0.012, 0.0, abs(rr - 0.22 - 0.01 * sin(t))) * 0.6);
  col = mix(col, A * 0.7 + B * 0.3, smoothstep(0.01, 0.0, abs(rr - 0.28)) * 0.4);
  for (int i = 0; i < 3; i++) { float fi = float(i);
    float base = hz + 0.06 - fi * 0.12; float h;
    if (st < 0.5) h = base + (0.22 - fi * 0.04) * max(0.0, 1.0 - abs(q.x - (fi - 1.0) * 0.22 - 0.05 * sin(s + fi)) * (3.0 + fi));
    else if (st < 1.5) h = base + (0.16 - fi * 0.03) * pow(peaks(q.x * (2.0 + fi) + fi * 3.0, s), 3.0) * 2.0;
    else if (st < 2.5) h = base + 0.05 * sin(q.x * (6.0 + fi * 3.0) + s + fi) + 0.03 * sin(q.x * 17.0 + fi);
    else h = base + (0.22 - fi * 0.05) * pow(peaks(q.x * (1.2 + fi * 0.7) + fi * 7.0, s + fi), 1.4);
    if (q.y < h) { col = mix(B * 0.8, C * 0.35, fi / 2.0); if (q.y > h - 0.006) col = mix(col, A, 0.7); }
  }
  if (st > 1.5 && st < 2.5) { float flick = step(0.6, fract(t * 0.4 + s)); float d = bolt(q, 0.05 * sin(s), 1.0, t * 0.4); col = mix(col, A, smoothstep(0.012, 0.004, d) * flick * step(hz - 0.1, q.y)); }
  return col + A * motes(q, float2(0.02, st < 0.5 ? 0.15 : -0.08), 20.0, 0.008, 0.85) * 0.8;
}

"""
private const val ART_AERIAL = """// Vue du ciel : caldeira, glacier crevassé, cyclone, carte topographique d'un sommet.
float3 aerial(float st, float2 q, float3 A, float3 B, float3 C) {
  float t = iTime; float s = iSeed; float r = length(q); float a = atan(q.y, q.x);
  if (st < 0.5) { float h = fbm(q * 4.0 + s); float ring = smoothstep(0.32, 0.28, r) * smoothstep(0.12, 0.16, r + 0.03 * h);
    float3 col = mix(C * 0.6 + B * 0.1 * h, C * 0.3, smoothstep(0.3, 0.6, r)); col = mix(col, C * 0.9 + B * 0.2 * h, ring);
    col = mix(col, mix(B, A, fbm(q * 10.0 + t * 0.2)), smoothstep(0.14, 0.12, r + 0.02 * h));
    col += mix(B, A, 0.4) * pow(abs(sin(a * 5.0 + s + h * 3.0)), 30.0) * smoothstep(0.14, 0.2, r) * smoothstep(0.7, 0.3, r) * (0.7 + 0.3 * sin(t * 2.0 - r * 20.0));
    return col + A * exp(-r * 6.0) * 0.4; }
  if (st < 1.5) { float h = fbm(q * 3.0 + s); float3 col = mix(A * 0.95, B * 0.6 + A * 0.3, smoothstep(0.2, 0.6, h + r * 0.4));
    float cr = smoothstep(0.02, 0.0, abs(fract(a * 1.9 + h * 0.8 + s) - 0.5) - 0.46) * smoothstep(0.08, 0.2, r);
    col = mix(col, C * 0.8 + B * 0.3, cr * 0.8); col += B * 0.25 * smoothstep(0.01, 0.0, abs(fract(r * 6.0 - h) - 0.5) - 0.48);
    return col + A * motes(q, float2(0.03, -0.03), 24.0, 0.006, 0.7); }
  if (st < 2.5) { float sp = sin(a * 2.0 + log(r + 0.02) * 6.0 - t * 1.2 + s); float cl = fbm(float2(a * 2.0, r * 6.0 - t * 0.3) + s);
    float3 col = mix(C, mix(C * 1.6 + B * 0.2, A * 0.8, cl), smoothstep(0.0, 1.0, sp) * smoothstep(0.05, 0.12, r));
    col = mix(col, C * 0.5, smoothstep(0.07, 0.04, r));
    return col + B * step(0.75, fract(t * 0.5 + s)) * 0.25 * smoothstep(0.6, 0.0, r); }
  float h = fbm(q * 2.2 + s) - r * 0.6; float3 col = pal(h * 1.2 + 0.5, A, B, C);
  col = mix(col, C * 0.6, smoothstep(0.06, 0.0, abs(fract(h * 14.0) - 0.5)) * 0.7);
  col = mix(col, A, smoothstep(0.03, 0.0, abs(fract(h * 2.8) - 0.5) - 0.46) * 0.6);
  return col + A * smoothstep(0.03, 0.0, r - 0.02);
}

"""
private const val ART_FACETS = """float3 facets(float2 q, float2 uv, float3 A, float3 B, float3 C) {
  float2 g = q * 5.0 + iSeed; g.x += g.y * 0.577; float2 i = floor(g); float2 f = fract(g); float tri = step(f.x, f.y);
  return pal(0.18 + 0.55 * uv.y + 0.32 * h21(i + tri * 17.0), A, B, C);
}

"""
private const val ART_MAIN_TOP = """half4 main(float2 fc) {
  float2 uv = fc / iRes; float2 p = (fc - 0.5 * iRes) / iRes.y; float2 q = float2(p.x, -p.y);
"""
private const val ART_DUO = """  {
    float3 c2 = scene2(q, float2(uv.x, 1.0 - uv.y), dA.rgb, dB.rgb, dC.rgb, iF2, iCam2);
    float d = q.x * 0.85 - q.y * 0.55 + 0.035 * sin(q.y * 14.0 + iTime * 1.5) + 0.05 * (fbm(q * 5.0 + iSeed) - 0.5);
    col = mix(col, c2, smoothstep(-0.006, 0.006, d));
    float seam = exp(-abs(d) * 55.0);
    col += mix(cA.rgb, dA.rgb, 0.5 + 0.5 * sin(q.y * 9.0 - iTime * 2.0)) * seam * 1.1 + float3(1.0) * pow(seam, 6.0) * 0.6;
  }
"""
private const val ART_MAIN_END = """  // Style par rareté : commune mate et granuleuse, peu commune pleine, rare lumineuse (rayons, éclats), épique irisée.
  float lum = dot(col, float3(0.299, 0.587, 0.114));
  if (iRar < 0.5) {
    col = mix(float3(lum), col, 0.82); col = mix(col, floor(col * 10.0 + 0.5) / 10.0, 0.25); col += (h21(fc * 0.37) - 0.5) * 0.035;
  } else if (iRar < 1.5) {
    col = mix(float3(lum), col, 0.95) * 1.02;
  } else {
    col = mix(float3(lum), col, 1.18);
    float ang = atan(q.x + 0.1 * sin(iSeed), 0.85 - q.y);
    col += cA.rgb * pow(abs(sin(ang * 7.0 + iTime * 0.25 + iSeed)), 12.0) * smoothstep(-0.3, 0.5, q.y) * 0.2;
    col += float3(1.0) * (stars(q + float2(0.0, iTime * 0.01), 38.0) + stars(q * 1.7 + 2.0, 24.0)) * 0.7;
    if (iRar > 2.5) {
      float k = uv.x * 1.3 + uv.y * 0.7 + iTilt.x * 0.8 + iTime * 0.05;
      col += clamp(abs(fract(k + float3(0.0, 0.333, 0.667)) * 6.0 - 3.0) - 1.0, 0.0, 1.0) * 0.1 * (0.6 + 0.4 * sin(k * 9.0));
    }
  }
  // Variante chromatique : la teinte tourne en vagues sur la carte (et avec l'inclinaison), bord prismatique.
  if (iChroma > 0.5) {
    float a = (uv.x * 1.6 + uv.y * 0.9) * 3.0 + iTime * 0.35 + iTilt.x * 2.0 + 0.6 * fbm(q * 2.0 + iSeed);
    float3 yiq = float3(dot(col, float3(0.299, 0.587, 0.114)), dot(col, float3(0.596, -0.274, -0.322)), dot(col, float3(0.211, -0.523, 0.312)));
    float ca = cos(a); float sa = sin(a);
    yiq.yz = float2(yiq.y * ca - yiq.z * sa, yiq.y * sa + yiq.z * ca) * 1.45;
    col = float3(yiq.x + 0.956 * yiq.y + 0.621 * yiq.z, yiq.x - 0.272 * yiq.y - 0.647 * yiq.z, yiq.x - 1.106 * yiq.y + 1.703 * yiq.z);
    float3 prism = clamp(abs(fract(a * 0.16 + float3(0.0, 0.333, 0.667)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    col += prism * (0.1 + 0.18 * smoothstep(0.5, 1.0, length(p * float2(1.25, 0.9))));
  }
  float sweep = smoothstep(0.35, 0.0, abs(uv.x * 0.8 + uv.y * 0.6 - 0.7 - iTilt.x * 0.5 + iTilt.y * 0.3));
  col += sweep * 0.05 * (1.0 + iRar * 0.4);
  float vig = smoothstep(1.15, 0.35, length(p * float2(1.0, 0.8)));
  return half4(half3(clamp(col * (0.78 + 0.22 * vig), 0.0, 1.0)), 1.0);
}
"""

/** Clé d'une variante de shader : biome et cadrage de chaque moitié (-1 : fond neutre ; pas de seconde moitié). */
internal data class ArtKey(val e1: Int, val f1: Int, val e2: Int = -2, val f2: Int = 0)
internal fun artKey(card: CardDef) = if (card.neutral) ArtKey(-1, 0) else ArtKey(card.element.ordinal, card.look.frame, card.element2?.ordinal ?: -2, card.look2?.frame ?: 0)

/** Source AGSL d'une variante : seulement les biomes et cadrages utilisés, le biome fixé en dur dans chaque fonction. */
internal fun artSource(key: ArtKey): String = buildString {
    append(ART_HEAD)
    val biomes = listOf(key.e1, key.e2).filter { it >= 0 }.distinct()
    biomes.forEach { append(ART_BIOMES[it]) }
    fun specialize(fn: String, name: String, e: Int) = fn.replace("float3 $name(float st, ", "float3 ${name}_$e(").replace(Regex("\\bst\\b"), "$e.0")
    val modes = mutableSetOf<Pair<Int, Int>>()
    fun scene(tag: String, e: Int, frame: Int) {
        if (e >= 0 && frame in 1..3 && modes.add(e to frame)) append(specialize(when (frame) { 1 -> ART_MACRO; 2 -> ART_POSTER; else -> ART_AERIAL }, when (frame) { 1 -> "macro"; 2 -> "poster"; else -> "aerial" }, e))
        if (e < 0 && "facets" !in this) append(ART_FACETS)
        val b = if (e >= 0) BIOME_FN[e] else ""
        val expr = when {
            e < 0 -> "facets(q, uv, A, B, C)"
            frame == 1 -> "macro_$e(q, A, B, C)"
            frame == 2 -> "poster_$e(q, A, B, C, cam.x)"
            frame == 3 -> "aerial_$e(q, A, B, C)"
            frame == 4 -> "(q.y < cam.x - 0.12) ? $b(float2(q.x + 0.006 * sin(q.y * 120.0 + iTime * 2.0), 2.0 * (cam.x - 0.12) - q.y), A, B, C, F, cam.x) * 0.5 + B * 0.05 + A * 0.3 * smoothstep(0.004, 0.0, abs(q.y - cam.x + 0.12)) : $b(q, A, B, C, F, cam.x)"
            else -> "$b(q, A, B, C, F, cam.x)"
        }
        append("\nfloat3 $tag(float2 q, float2 uv, float3 A, float3 B, float3 C, float4 F, float3 cam) { q = q / cam.y; return $expr; }\n")
    }
    scene("scene1", key.e1, key.f1)
    if (key.e2 >= 0) scene("scene2", key.e2, key.f2)
    append(ART_MAIN_TOP)
    append("  float3 col = scene1(q, float2(uv.x, 1.0 - uv.y), cA.rgb, cB.rgb, cC.rgb, iF, iCam);\n")
    if (key.e2 >= 0) append(ART_DUO)
    append(ART_MAIN_END)
}


/** Reflet holographique, posé en mode « écran » sur les cartes brillantes et animées. */
private const val HOLO = """
uniform float2 iRes; uniform float iTime; uniform float2 iTilt; uniform float iSpark;
float h21(float2 p) { p = mod(p, 289.0); float3 p3 = fract(float3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
half3 hue(float h) { return half3(clamp(abs(fract(h + float3(0.0, 0.333, 0.667)) * 6.0 - 3.0) - 1.0, 0.0, 1.0)); }
half4 main(float2 fc) {
  float2 uv = fc / iRes; float k = uv.x * 0.9 + uv.y * 0.55 + iTilt.x * 0.9 - iTilt.y * 0.6;
  float band = pow(1.0 - abs(fract(k * 0.8) * 2.0 - 1.0), 3.0);
  half3 c = hue(fract(k * 1.4 + iTime * 0.02)) * half(0.42 * band);
  float2 g = floor(fc / 9.0); float h = h21(g); float tw = sin(iTime * 3.0 + h * 60.0 + iTilt.x * 8.0);
  float dot0 = smoothstep(0.5, 0.0, length(fract(fc / 9.0) - 0.5));
  c += half(iSpark * step(0.985, h) * smoothstep(0.6, 1.0, tw) * dot0 * dot0 * 1.2);
  c += half(0.10 * smoothstep(0.25, 0.0, abs(uv.x + uv.y - 1.0 - iTilt.x * 1.2)));
  return half4(c, 1.0);
}
"""

private fun Long.toColor() = Color(this.toInt())

/**
 * Shaders compilés une seule fois pour toute l'app : compiler la scène des biomes coûte plusieurs centaines de
 * millisecondes, et chaque carte affichée la recompilait. Les uniformes sont relus à chaque dessin, ce qui permet de
 * partager l'objet entre toutes les cartes. Skia (iOS et ordinateur) lit le même langage que l'AGSL d'Android.
 */
private object ArtShaders {
    private val variants = ConcurrentHashMap<ArtKey, CardShader?>()
    fun art(card: CardDef): CardShader? = variants.getOrPut(artKey(card)) { runCatching { CardShader(artSource(artKey(card))) }.getOrNull() }
    val holo by lazy { runCatching { CardShader(HOLO) }.getOrNull() }
    /** Prépare les variantes ; une variante qui ne compile pas garde le dégradé. */
    fun warm(cards: List<CardDef>) = cards.distinctBy(::artKey).forEach { c -> art(c) }
}

/** Compile les shaders en arrière-plan avant la première carte, pour qu'aucun écran ne fige en l'attendant. */
fun warmCardShaders() {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { runCatching { ArtShaders.holo; ArtShaders.warm(app.superhealth.cards.Booster.entries.map { it.art() } + listOfNotNull(app.superhealth.cards.CardStore.background()?.card) +
        app.superhealth.cards.Catalog.cards + app.superhealth.cards.Tier.entries.map { app.superhealth.cards.Catalog.defaultFor(it) }) } }
}

/** Sources des shaders de cartes, pour les vérifier à la compilation (tests). */
internal fun allCardShaderSources(): List<String> = (app.superhealth.cards.Catalog.cards + app.superhealth.cards.Tier.entries.map { app.superhealth.cards.Catalog.defaultFor(it) })
    .map(::artKey).distinct().map(::artSource) + HOLO

private fun CardShader.color(name: String, c: Color) = setFloatUniform(name, c.red, c.green, c.blue, 1f)
private fun CardShader.look(f: String, cam: String, l: app.superhealth.cards.Look) {
    fun bit(b: Int) = if (l.features and b != 0) 1f else 0f
    setFloatUniform(f, bit(1), bit(2), bit(4), bit(8)); setFloatUniform(cam, l.horizon, l.zoom, l.frame.toFloat())
}

/** Fond d'une carte. [time] fige l'animation à 0 pour les finitions normales. */
@Composable
fun CardArt(card: CardDef, finish: Finish, modifier: Modifier = Modifier, tilt: () -> Offset = { Offset.Zero }, time: () -> Float = { 0f }, holo: Boolean = finish != Finish.NORMALE,
            resolution: Float = CardPerf.artScale) {
    val shaders = ArtShaders
    val compiled = remember(artKey(card)) { shaders.art(card) }
    if (compiled != null) {
        // Le shader coûte au pixel : il est calculé sur un calque réduit puis agrandi ; textes et cadres, dessinés à part, restent nets.
        BoxWithConstraints(modifier) {
        val k = resolution.coerceIn(0.25f, 1f)
        Box((if (k < 1f) Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(maxWidth * k, maxHeight * k).graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen; transformOrigin = TransformOrigin(0f, 0f); scaleX = 1f / k; scaleY = 1f / k
        } else Modifier.fillMaxSize()).drawWithCache {
            val art = compiled; val glare = shaders.holo
            onDrawBehind {
                art.setFloatUniform("iRes", size.width, size.height)
                art.setFloatUniform("iTime", time())
                art.setFloatUniform("iSeed", card.seed)
                val tl = tilt(); art.setFloatUniform("iTilt", tl.x, tl.y)
                art.setFloatUniform("iStyle", if (card.neutral) 8f else card.element.ordinal.toFloat())
                art.setFloatUniform("iStyle2", card.element2?.ordinal?.toFloat() ?: -1f)
                art.setFloatUniform("iRar", card.rarity.ordinal.toFloat())
                art.setFloatUniform("iChroma", if (finish == Finish.CHROMATIQUE) 1f else 0f)
                art.color("cA", card.palette.a.toColor()); art.color("cB", card.palette.b.toColor()); art.color("cC", card.palette.c.toColor())
                val p2 = card.palette2 ?: card.palette
                art.color("dA", p2.a.toColor()); art.color("dB", p2.b.toColor()); art.color("dC", p2.c.toColor())
                art.look("iF", "iCam", card.look); art.look("iF2", "iCam2", card.look2 ?: card.look)
                drawRect(art.brush())
                if (holo && glare != null) {
                    glare.setFloatUniform("iRes", size.width, size.height)
                    glare.setFloatUniform("iTime", time())
                    glare.setFloatUniform("iTilt", tl.x, tl.y)
                    glare.setFloatUniform("iSpark", if (finish >= Finish.ANIMEE) 1f else 0.5f)
                    drawRect(glare.brush(), blendMode = BlendMode.Screen)
                }
            }
        })
        }
    } else {
        val p = card.palette
        Box(modifier.drawWithCache {
            val colors = listOf(p.a.toColor(), p.b.toColor(), p.c.toColor()) + (card.palette2?.let { listOf(it.b.toColor(), it.a.toColor()) } ?: emptyList())
            val brush = Brush.linearGradient(colors, Offset(size.width * 0.2f, 0f), Offset(size.width, size.height))
            onDrawBehind { drawRect(brush) }
        })
    }
}

/** Horloge d'animation en secondes, arrêtée quand [running] est faux (économie de batterie). */
@Composable
fun rememberCardTime(running: Boolean = true): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val ctx = LocalContext.current
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        CardPerf.init(ctx)
        // En mode léger, l'horloge n'avance qu'à 30 images par seconde : les fonds se redessinent deux fois moins.
        val step = if (CardPerf.lite) 33_000_000L else 0L
        val start = withFrameNanos { it } - (time.floatValue * 1e9f).toLong()
        var last = 0L
        while (true) withFrameNanos { if (it - last >= step) { last = it; time.floatValue = (it - start) / 1e9f } }
    }
    return time
}

/**
 * Réglage des effets selon le téléphone : en mode léger (peu de mémoire ou de cœurs), les fonds tournent à 30 images
 * par seconde, le décor plein écran est calculé en plus basse résolution et les particules sont moins nombreuses.
 */
object CardPerf {
    var lite = false; private set
    private var ready = false
    fun init(ctx: Context) {
        if (ready) return
        // iPhone récent : effets complets ; les appareils à moins de six cœurs passent en mode léger.
        lite = cpuCores() < 6
        ready = true
        warmCardShaders()
    }
    /** Nombre de particules d'un effet, réduit en mode léger. */
    fun particles(n: Int) = if (lite) n / 2 else n
    /** Échelle de rendu du décor plein écran (le shader coûte au pixel ; flouté par l'assombrissement, il n'y paraît pas). */
    val backdropScale get() = if (lite) 0.33f else 0.5f
    /** Échelle de rendu des fonds de carte. */
    val artScale get() = if (lite) 0.5f else 0.7f
}

/** Inclinaison du téléphone lissée, de -1 à 1 sur chaque axe (attitude de CoreMotion sur iOS). */
@Composable
fun rememberTilt(enabled: Boolean = true): State<Offset> {
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        var base: Pair<Float, Float>? = null
        val stop = startTilt { roll, pitch ->
            val b = base ?: (roll to pitch).also { base = it }
            val target = Offset(((roll - b.first) / 0.6f).coerceIn(-1f, 1f), ((pitch - b.second) / 0.6f).coerceIn(-1f, 1f))
            val cur = tilt.value
            tilt.value = Offset(cur.x + (target.x - cur.x) * 0.15f, cur.y + (target.y - cur.y) * 0.15f)
            // Le repère suit lentement la position de repos pour que la carte revienne au centre.
            base = (b.first + (roll - b.first) * 0.01f) to (b.second + (pitch - b.second) * 0.01f)
        }
        onDispose { stop() }
    }
    return tilt
}

internal fun Offset.magnitude() = abs(x) + abs(y)
