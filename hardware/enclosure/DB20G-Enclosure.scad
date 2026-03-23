// =========================================================================
// DB20-G Interface Box — 3D-Printable Enclosure
// OpenSCAD Parametric Design
// License: CERN-OHL-P-2.0
// =========================================================================
//
// Designed for the DB20-G Interface Box PCB rev2 (46x73.5mm).
// Measured from KiCad PCB layout (DB20G-Interface.kicad_pcb).
//
// Connector layout by board edge:
//   Top edge (Y=0):    J2 (RJ-45 Radio)
//   Bottom edge (Y=73.5): J1 (USB-C), J3 (RJ-45 Handset)
//   Right edge (X=46):  LED1–LED4 (vertical column)
//   Left edge (X=0):    Clear
//
// Tallest component: K1 relay (~17mm above PCB).
//
// Assembly:
//   1. Place PCB on standoffs, screw down with 4x M3 screws
//   2. Press-fit lid onto base — snap clips hold it in place
//   3. To remove lid, gently flex long walls outward
//
// Print Settings:
//   Material: PETG or ABS recommended (heat resistance, clip flex)
//   Layer Height: 0.2mm
//   Infill: 20%
//   Supports: Not required
//   Orientation: Print base upside-down, lid right-side-up
//
// Usage:
//   Render base: set RENDER_PART = "base";
//   Render lid:  set RENDER_PART = "lid";
//   Render both: set RENDER_PART = "both";

// =========================================================================
// PARAMETERS — Adjust these to fine-tune the design
// =========================================================================

// What to render: "base", "lid", or "both"
RENDER_PART = "both";

// PCB dimensions (mm) — measured from KiCad Edge.Cuts
pcb_length = 46;       // X axis (board width)
pcb_width  = 73.5;     // Y axis (board depth)
pcb_thick  = 1.6;      // PCB thickness (FR4)

// Enclosure parameters
wall        = 2.0;     // Wall thickness
floor       = 1.5;     // Floor/ceiling thickness
tolerance   = 0.3;     // Clearance around PCB
pcb_standoff = 3.0;    // Height of PCB standoff posts

// Component clearance above PCB (relay K1 is ~17mm)
component_clearance = 18;

// Calculated internal dimensions
inner_x = pcb_length + 2*tolerance;
inner_y = pcb_width  + 2*tolerance;
inner_z = pcb_standoff + pcb_thick + component_clearance;

// Outer dimensions
outer_x = inner_x + 2*wall;
outer_y = inner_y + 2*wall;
outer_z = inner_z + floor;               // Base height
lid_z   = floor + 2;                     // Lid height (lip overlap)

// Corner radius
corner_r = 3.0;

// PCB screw parameters (M3 — board screws directly to standoffs)
screw_d = 3.2;         // M3 clearance hole through PCB
screw_pilot_d = 2.5;   // Self-tapping pilot hole in standoffs
screw_head_d = 6.0;    // M3 pan-head clearance
screw_post_d = 7.0;    // Standoff boss diameter

// Snap-fit detent parameters (lid retention)
// Small wedge bumps on inner walls catch below the lid lip.
// Wall flexes outward during insertion; bumps spring back to retain.
detent_w     = 8.0;    // Bump width along the wall
detent_h     = 0.6;    // Protrusion depth inward from wall face
detent_z     = 2.5;    // Total bump height (catch + ramp)
detent_catch = 0.8;    // Catch ledge height (flat retention face)

// PCB mounting hole positions (from PCB origin at top-left corner)
// Measured from KiCad footprint positions for MH1–MH4
mount_holes = [
    [ 4.26,  3.80],    // MH1 — top-left
    [42.50,  3.50],    // MH2 — top-right
    [ 3.70, 69.80],    // MH3 — bottom-left
    [42.36, 70.10],    // MH4 — bottom-right
];

// Z reference: top surface of PCB (all connectors sit on this)
pcb_top_z = floor + pcb_standoff + pcb_thick;  // = 1.5 + 3.0 + 1.6 = 6.1

// =========================================================================
// CONNECTOR CUTOUT DIMENSIONS (from KiCad footprint positions)
// =========================================================================

// USB-C — J1, bottom wall (Y=pcb_width edge), center at board x=12.68
usbc_w = 9.5;          // USB-C plug width
usbc_h = 3.5;          // USB-C plug height
usbc_x = 12.68;        // Board-relative X center
usbc_z_center = pcb_top_z + usbc_h/2;   // center at 7.85, bottom at 6.1

// RJ-45 #1 — J2 Radio, top wall (Y=0 edge), center at board x=23.81
rj45_w = 16.0;         // RJ-45 jack width
rj45_h = 13.5;         // RJ-45 jack height
rj45_radio_x = 23.81;  // Board-relative X center
rj45_z_center = pcb_top_z + rj45_h/2;   // center at 12.85, bottom at 6.1

// RJ-45 #2 — J3 Handset, bottom wall (Y=pcb_width edge), center at board x=32.41
rj45_handset_x = 32.41;

// LED light pipe holes — right wall (X=pcb_length edge)
// LED1–LED4 at board Y positions 31.0, 34.0, 37.0, 40.0 (3mm spacing)
led_d = 3.0;           // LED light pipe hole diameter
led_positions_y = [31.0, 34.0, 37.0, 40.0];
led_z = pcb_top_z + 1.5;                // LED center ~1.5mm above PCB

// Ventilation slots (floor)
vent_count = 6;
vent_w = 1.2;
vent_l = 20;

// =========================================================================
// HELPER: Convert board-relative X/Y to enclosure-centered coordinates
// =========================================================================
// The enclosure is centered at origin. Board origin is at (tolerance+wall)
// inset from the -X/-Y inner corner.

function board_to_enc_x(bx) = bx + tolerance + wall - outer_x/2;
function board_to_enc_y(by) = by + tolerance + wall - outer_y/2;

// =========================================================================
// MODULES
// =========================================================================

// Rounded rectangle (2D profile)
module rounded_rect(x, y, r) {
    offset(r) offset(-r) square([x, y], center=true);
}

// Rounded box (3D)
module rounded_box(x, y, z, r) {
    linear_extrude(z)
        rounded_rect(x, y, r);
}

// Screw post (cylinder with hole)
module screw_post(od, id, h) {
    difference() {
        cylinder(d=od, h=h, $fn=24);
        cylinder(d=id, h=h+1, $fn=24);
    }
}

// =========================================================================
// BASE (bottom half of enclosure)
// =========================================================================
module base() {
    difference() {
        union() {
            // Main shell
            difference() {
                rounded_box(outer_x, outer_y, outer_z, corner_r);
                translate([0, 0, floor])
                    rounded_box(inner_x, inner_y, outer_z, corner_r - wall/2);
            }

            // PCB mounting standoffs — M3 screws go through the PCB
            // mounting holes and self-tap into 2.5mm pilot holes.
            for (pos = mount_holes) {
                translate([
                    board_to_enc_x(pos[0]),
                    board_to_enc_y(pos[1]),
                    floor
                ])
                screw_post(screw_post_d, screw_pilot_d, pcb_standoff);
            }

            // Snap-fit detent bumps — wedge-shaped bumps on inner walls
            // catch below the lid lip to retain the lid.
            // Long walls (±Y) — two bumps per wall
            for (xoff = [-inner_x/4, inner_x/4]) {
                // +Y wall bumps (protrude inward = -Y)
                translate([xoff, inner_y/2, outer_z - 1.5 - detent_catch])
                    detent_bump(180);
                // -Y wall bumps (protrude inward = +Y)
                translate([xoff, -inner_y/2, outer_z - 1.5 - detent_catch])
                    detent_bump(0);
            }
            // Short walls (±X) — one bump per wall
            // +X wall bump (protrude inward = -X)
            translate([inner_x/2, 0, outer_z - 1.5 - detent_catch])
                detent_bump(90);
            // -X wall bump (protrude inward = +X)
            translate([-inner_x/2, 0, outer_z - 1.5 - detent_catch])
                detent_bump(270);
        }

        // --- Connector Cutouts ---
        // Board Y=0 edge → enclosure -Y wall; Board Y=73.5 edge → enclosure +Y wall

        // USB-C — J1, board bottom edge (Y=73.5 → +Y wall)
        translate([board_to_enc_x(usbc_x), outer_y/2 - wall - 0.5, usbc_z_center])
            rotate([-90, 0, 0])
            linear_extrude(wall + 2)
            rounded_rect(usbc_w + 1, usbc_h + 1, 1.0);

        // RJ-45 #1 Radio — J2, board top edge (Y=0 → -Y wall)
        translate([board_to_enc_x(rj45_radio_x), -(outer_y/2 + 1), rj45_z_center])
            rotate([-90, 0, 0])
            linear_extrude(wall + 2)
            rounded_rect(rj45_w + 1, rj45_h + 1, 1.0);

        // RJ-45 #2 Handset — J3, board bottom edge (Y=73.5 → +Y wall)
        translate([board_to_enc_x(rj45_handset_x), outer_y/2 - wall - 0.5, rj45_z_center])
            rotate([-90, 0, 0])
            linear_extrude(wall + 2)
            rounded_rect(rj45_w + 1, rj45_h + 1, 1.0);

        // LED light pipe holes (right wall, +X side)
        for (ly = led_positions_y) {
            translate([
                outer_x/2 - wall - 0.5,
                board_to_enc_y(ly),
                led_z
            ])
            rotate([0, 90, 0])
            cylinder(d=led_d, h=wall + 2, $fn=24);
        }

        // Ventilation slots (floor)
        for (i = [0:vent_count-1]) {
            translate([
                -vent_l/2,
                (i - (vent_count-1)/2) * (vent_w * 3),
                -0.5
            ])
            cube([vent_l, vent_w, floor + 1]);
        }

        // Cable strain-relief slot (+Y wall, next to USB-C)
        translate([board_to_enc_x(usbc_x), outer_y/2 - wall - 0.5, usbc_z_center + usbc_h])
            rotate([-90, 0, 0])
            linear_extrude(wall + 2)
            square([12, 2], center=true);

        // Lid alignment rabbet (recessed shelf around top inside edge)
        translate([0, 0, outer_z - 1.5])
            difference() {
                rounded_box(inner_x + 0.4, inner_y + 0.4, 2, corner_r - wall/2);
                rounded_box(inner_x - 2, inner_y - 2, 2.5, corner_r - wall);
            }


    }
}

// =========================================================================
// DETENT BUMP MODULE
// =========================================================================
// Wedge-shaped bump for snap-fit lid retention.
// Bottom is a flat catch ledge; top ramps to flush with the wall.
// 'angle' sets protrusion direction: 0=+Y, 90=-X, 180=-Y, 270=+X.
module detent_bump(angle) {
    rotate([0, 0, angle])
    hull() {
        // Catch ledge (full protrusion at bottom)
        translate([0, detent_h/2, detent_catch/2])
            cube([detent_w, detent_h, detent_catch], center=true);
        // Ramp peak (flush with wall face at top)
        translate([0, 0.01, detent_z - 0.01])
            cube([detent_w, 0.02, 0.02], center=true);
    }
}

// =========================================================================
// LID (top half — snap-fit, no screws)
// =========================================================================
module lid() {
    difference() {
        union() {
            // Main lid plate
            rounded_box(outer_x, outer_y, floor, corner_r);

            // Alignment lip (fits inside base rabbet)
            translate([0, 0, -1.5])
                difference() {
                    rounded_box(inner_x - 0.2, inner_y - 0.2, 1.5, corner_r - wall/2 - 0.2);
                    rounded_box(inner_x - 2.4, inner_y - 2.4, 2, corner_r - wall - 0.2);
                }


        }

        // Label engraving (top surface)
        translate([0, 8, floor - 0.3])
            linear_extrude(0.5)
            text("DB20-G", size=6, halign="center", valign="center", font="Liberation Mono:style=Bold");

        translate([0, 0, floor - 0.3])
            linear_extrude(0.5)
            text("Interface Box", size=3.5, halign="center", valign="center", font="Liberation Mono");

        translate([0, -6, floor - 0.3])
            linear_extrude(0.5)
            text("v2.0", size=2.5, halign="center", valign="center", font="Liberation Mono");

        // Ventilation slots (lid top)
        for (i = [0:3]) {
            translate([
                -vent_l/2,
                (i - 1.5) * (vent_w * 3) - 16,
                -0.5
            ])
            cube([vent_l, vent_w, floor + 1]);
        }
    }
}

// =========================================================================
// RENDER
// =========================================================================

if (RENDER_PART == "base") {
    base();
}
else if (RENDER_PART == "lid") {
    translate([0, 0, floor])
        rotate([180, 0, 0])
        lid();
}
else {  // "both" — exploded view
    base();
    translate([0, 0, outer_z + 10])
        lid();
}
