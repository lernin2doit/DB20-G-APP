// =========================================================================
// DB20-G Interface Box — 3D-Printable Enclosure
// OpenSCAD Parametric Design
// License: CERN-OHL-P-2.0
// =========================================================================
//
// Designed for the DB20-G Interface Box PCB (65x45mm).
// Connectors: USB-C (left), 2x RJ-45 (right), 3.5mm TRS (top),
//             4x LEDs visible through bottom edge.
//
// Print Settings:
//   Material: PETG or ABS recommended (heat resistance)
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

// PCB dimensions (mm)
pcb_length = 65;       // X axis
pcb_width  = 45;       // Y axis
pcb_thick  = 1.6;      // PCB thickness

// Enclosure parameters
wall        = 2.0;     // Wall thickness
floor       = 1.5;     // Floor/ceiling thickness
tolerance   = 0.3;     // Clearance around PCB
pcb_standoff = 3.0;    // Height of PCB standoff posts

// Calculated internal dimensions
inner_x = pcb_length + 2*tolerance;
inner_y = pcb_width  + 2*tolerance;
inner_z = pcb_standoff + pcb_thick + 12; // 12mm component clearance above PCB

// Outer dimensions
outer_x = inner_x + 2*wall;
outer_y = inner_y + 2*wall;
outer_z = inner_z + floor;               // Base height
lid_z   = floor + 2;                     // Lid height (lip overlap)

// Corner radius
corner_r = 3.0;

// Screw parameters (M3)
screw_d = 3.2;         // M3 screw clearance hole
screw_head_d = 6.0;    // M3 screw head clearance
screw_post_d = 7.0;    // Screw boss diameter
screw_depth  = 8.0;    // Screw hole depth in base

// PCB mounting hole positions (from PCB corner, matching KiCad)
// Mounting holes at 4mm from each edge
mh_inset_x = 4.0;
mh_inset_y = 4.0;
mount_holes = [
    [mh_inset_x,              mh_inset_y],               // Bottom-left
    [pcb_length - mh_inset_x, mh_inset_y],               // Bottom-right
    [mh_inset_x,              pcb_width - mh_inset_y],    // Top-left
    [pcb_length - mh_inset_x, pcb_width - mh_inset_y],   // Top-right
];

// =========================================================================
// CONNECTOR CUTOUT DIMENSIONS
// =========================================================================

// USB-C (left wall, centered vertically)
usbc_w = 9.5;          // Width of USB-C plug
usbc_h = 3.5;          // Height of USB-C plug
usbc_y_center = inner_y / 2;
usbc_z_center = floor + pcb_standoff + pcb_thick + usbc_h/2 + 0.5;

// RJ-45 connectors (right wall)
rj45_w = 16.0;         // RJ-45 jack width
rj45_h = 13.5;         // RJ-45 jack height
rj45_1_y = inner_y * 0.3;  // First RJ-45 (Radio) center Y
rj45_2_y = inner_y * 0.7;  // Second RJ-45 (Handset) center Y
rj45_z_center = floor + pcb_standoff + pcb_thick + rj45_h/2 - 1;

// 3.5mm TRS audio jack (top wall)
trs_d  = 6.5;          // 3.5mm jack barrel diameter
trs_x_center = inner_x * 0.5;
trs_z_center = floor + pcb_standoff + pcb_thick + trs_d/2 + 0.5;

// LED light pipes (front/bottom wall)
led_d = 3.0;           // LED light pipe hole diameter
led_count = 4;
led_spacing = 5.0;
led_start_x = inner_x/2 - (led_count-1)*led_spacing/2;
led_z = floor + pcb_standoff + pcb_thick + 1.5;

// Ventilation slots
vent_count = 5;
vent_w = 1.2;
vent_l = 15;

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

            // PCB mounting standoffs
            for (pos = mount_holes) {
                translate([
                    pos[0] + tolerance + wall - outer_x/2,
                    pos[1] + tolerance + wall - outer_y/2,
                    floor
                ])
                screw_post(screw_post_d, screw_d, pcb_standoff);
            }

            // Lid screw bosses (at corners, slightly inset)
            for (dx = [-1, 1], dy = [-1, 1]) {
                translate([
                    dx * (outer_x/2 - wall - screw_post_d/2),
                    dy * (outer_y/2 - wall - screw_post_d/2),
                    floor
                ])
                screw_post(screw_post_d, screw_d, inner_z);
            }
        }

        // --- Connector Cutouts ---

        // USB-C (left wall, -X side)
        translate([-(outer_x/2 + 1), 0, usbc_z_center])
            rotate([0, 90, 0])
            linear_extrude(wall + 2)
            rounded_rect(usbc_h + 1, usbc_w + 1, 1.0);

        // RJ-45 #1 - Radio (right wall, +X side)
        translate([outer_x/2 - wall - 0.5, rj45_1_y - inner_y/2, rj45_z_center])
            rotate([0, 90, 0])
            linear_extrude(wall + 2)
            rounded_rect(rj45_h + 1, rj45_w + 1, 1.0);

        // RJ-45 #2 - Handset (right wall, +X side)
        translate([outer_x/2 - wall - 0.5, rj45_2_y - inner_y/2, rj45_z_center])
            rotate([0, 90, 0])
            linear_extrude(wall + 2)
            rounded_rect(rj45_h + 1, rj45_w + 1, 1.0);

        // 3.5mm TRS (top wall, +Y side)
        translate([trs_x_center - inner_x/2, outer_y/2 - wall - 0.5, trs_z_center])
            rotate([90, 0, 0])
            cylinder(d=trs_d + 1, h=wall + 2, $fn=24);

        // LED light pipe holes (front wall, -Y side)
        for (i = [0:led_count-1]) {
            translate([
                led_start_x + i*led_spacing - inner_x/2,
                -(outer_y/2 + 1),
                led_z
            ])
            rotate([-90, 0, 0])
            cylinder(d=led_d, h=wall + 2, $fn=24);
        }

        // Ventilation slots (bottom)
        for (i = [0:vent_count-1]) {
            translate([
                -vent_l/2,
                (i - (vent_count-1)/2) * (vent_w * 3),
                -0.5
            ])
            cube([vent_l, vent_w, floor + 1]);
        }

        // Lid alignment lip (rabbet joint around top edge)
        translate([0, 0, outer_z - 1.5])
            difference() {
                rounded_box(inner_x + 0.4, inner_y + 0.4, 2, corner_r - wall/2);
                rounded_box(inner_x - 2, inner_y - 2, 2.5, corner_r - wall);
            }
    }
}

// =========================================================================
// LID (top half of enclosure)
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

        // Screw holes (countersunk for M3)
        for (dx = [-1, 1], dy = [-1, 1]) {
            translate([
                dx * (outer_x/2 - wall - screw_post_d/2),
                dy * (outer_y/2 - wall - screw_post_d/2),
                -2
            ]) {
                cylinder(d=screw_d, h=floor + 4, $fn=24);
                // Countersink
                translate([0, 0, floor - 1])
                    cylinder(d1=screw_d, d2=screw_head_d, h=2, $fn=24);
            }
        }

        // Label engraving (top surface)
        translate([0, 5, floor - 0.3])
            linear_extrude(0.5)
            text("DB20-G", size=5, halign="center", valign="center", font="Liberation Mono:style=Bold");

        translate([0, -2, floor - 0.3])
            linear_extrude(0.5)
            text("Interface Box", size=3, halign="center", valign="center", font="Liberation Mono");

        translate([0, -7, floor - 0.3])
            linear_extrude(0.5)
            text("v1.0", size=2.5, halign="center", valign="center", font="Liberation Mono");
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
