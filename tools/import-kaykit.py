"""Convert the CC0 KayKit miniatures for the Arsenal viewer.

Run with Blender in background:
  blender -b -t 2 --python tools/import-kaykit.py -- <Adventurers zip> <Skeletons zip> <Arsenal assets>
"""
from pathlib import Path
import array
import bpy
import json
from math import pi
from mathutils import Matrix, Vector
import subprocess
import sys
from tempfile import TemporaryDirectory
from zipfile import ZipFile

ADVENTURERS, SKELETONS, OUT = map(Path, sys.argv[sys.argv.index("--") + 1:])
MODELS = (
    (SKELETONS, "skeletonminion", "Skeleton_Minion", "skeleton", ("Skeleton_Axe", None)),
    (SKELETONS, "skeletonwarrior", "Skeleton_Warrior", "skeleton", ("Skeleton_Blade", "Skeleton_Shield_Small_A")),
    (SKELETONS, "skeletonmage", "Skeleton_Mage", "skeleton", ("Skeleton_Staff", None)),
    (SKELETONS, "skeletonrogue", "Skeleton_Rogue", "skeleton", ("Skeleton_Dagger", None)),
    (SKELETONS, "skeletongolem", "Skeleton_Golem", "skeleton", ("Skeleton_Golem_Axe_Large", None)),
    (SKELETONS, "necromancer", "Necromancer", "skeleton", ("Skeleton_Scythe", None)),
    (ADVENTURERS, "knight", "Knight", "knight", ("sword_1handed", "shield_badge")),
    (ADVENTURERS, "mage", "Mage", "mage", ("staff", None)),
    (ADVENTURERS, "barbarian", "Barbarian", "barbarian", ("axe_1handed", "shield_round_barbarian")),
    (ADVENTURERS, "ranger", "Ranger", "ranger", (None, "bow_withString")),
    (ADVENTURERS, "rogue", "Rogue_Hooded", "rogue", ("dagger", None)),
)

def unpack(archive, prefix, member, staging):
    target = staging / Path(member).name
    target.write_bytes(archive.read(prefix + member))
    return target


def import_asset(path):
    known = set(bpy.context.scene.objects)
    bpy.ops.import_scene.gltf(filepath=str(path))
    return set(bpy.context.scene.objects) - known


def grip_transform(prop, side, hand):
    """Place a rigid prop in the posed palm using its authored grip and axes."""
    outward = -1 if side == "r" else 1
    origin = Vector()
    authored = Matrix.Identity(4)
    size = 1.0
    if "shield" in prop:
        # Skeleton shields have the handle on the opposite side of the mesh.
        skeleton = prop.startswith("skeleton_")
        origin.y = -.085 if skeleton else .092
        size = .9
        authored = Matrix.Rotation(pi if skeleton else 0, 4, "Z")
        pose = Matrix.Rotation(.53, 4, "Z") @ Matrix.Rotation(.30, 4, "X")
    else:
        lean, pitch = .23, .21
        if "bow" in prop:
            authored = Matrix.Rotation(pi, 4, "Z") @ Matrix.Rotation(pi / 2, 4, "X")
            origin.y = -.27
            size, lean, pitch = .85, .36, .25
        elif "staff" in prop or "scythe" in prop:
            origin.z = -.46
            size, lean, pitch = .88, .45, .36
        elif "golem" in prop:
            size, lean, pitch = .90, .33, .20
        elif "axe" in prop:
            lean, pitch = .48, .42
        pose = Matrix.Rotation(outward * lean, 4, "Y") @ Matrix.Rotation(pitch, 4, "X")
    return Matrix.Translation(hand) @ pose @ authored @ Matrix.Scale(size, 4) @ Matrix.Translation(-origin)


def export_model(archive_path, slug, model, texture, props, staging):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    is_skeleton = archive_path == SKELETONS
    characters, items = ("characters", "assets") if is_skeleton else ("Characters", "Assets")
    with ZipFile(archive_path) as archive:
        prefix = archive_path.stem + "/"
        size = "Large" if model == "Skeleton_Golem" else "Medium"
        path = f"Animations/gltf/Rig_{size}/Rig_{size}_General.glb"
        import_asset(unpack(archive, prefix, path, staging))
        idle = bpy.data.actions["Idle_A"]
        objects = import_asset(unpack(archive, prefix, f"{characters}/gltf/{model}.glb", staging))
        rig = next(obj for obj in objects if obj.type == "ARMATURE")
        rig.animation_data_create()
        rig.animation_data.action = idle
        rig.animation_data.action_slot = idle.slots[0]
        bpy.context.scene.frame_set(1)
        bpy.context.view_layer.update()
        shoulders = {side: rig.matrix_world @ rig.pose.bones[f"upperarm.{side}"].head for side in ("l", "r")}
        arm_pose = {}
        for side, prop in zip(("r", "l"), props):
            if prop:
                shoulder = shoulders[side]
                outward = -1 if side == "r" else 1
                shield = "shield" in prop.lower()
                spread, reach = (.56, -.63) if shield else (.34, -.48)
                arm_pose[side] = (Matrix.Translation(shoulder) @ Matrix.Rotation(-outward * spread, 4, "Y")
                                  @ Matrix.Rotation(reach, 4, "X") @ Matrix.Translation(-shoulder))
        meshes = []
        for obj in objects:
            if obj.type != "MESH" or obj.parent != rig:
                continue
            side = "l" if "ArmLeft" in obj.name else "r" if "ArmRight" in obj.name else None
            meshes.append((obj, arm_pose.get(side), side))
        atlas_name = "skeleton_texture_A" if is_skeleton else texture + "_texture"
        atlas = bpy.data.images[atlas_name].packed_file.data
        png = staging / (texture + ".png")
        png.write_bytes(atlas)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(png), "-c:v", "libwebp", "-lossless", "1",
                        str(OUT / f"kaykit-{texture}.webp")], check=True)

        for side, prop in zip(("r", "l"), props):
            if not prop:
                continue
            member = f"{items}/gltf/{prop}.gltf"
            gltf = unpack(archive, prefix, member, staging)
            doc = json.loads(gltf.read_text())
            if any(Path(image["uri"]).stem != atlas_name for image in doc["images"]):
                continue
            for entry in doc["buffers"] + doc["images"]:
                unpack(archive, prefix, f"{items}/gltf/{entry['uri']}", staging)
            hand = rig.matrix_world @ rig.pose.bones[f"handslot.{side}"].head
            place = grip_transform(prop.lower(), side, arm_pose[side] @ hand)
            meshes.extend((obj, place, side) for obj in import_asset(gltf) if obj.type == "MESH")

    evaluated = bpy.context.evaluated_depsgraph_get()
    rows = []
    for obj, attach, side in sorted(meshes, key=lambda entry: entry[0].name):
        evaluated_obj = obj.evaluated_get(evaluated)
        mesh = evaluated_obj.to_mesh()
        mesh.calc_loop_triangles()
        world = evaluated_obj.matrix_world if attach is None else attach @ evaluated_obj.matrix_world
        normals = world.to_3x3().inverted_safe().transposed()
        uvs = mesh.uv_layers.active.data
        name = obj.name
        bone = (2 if side == "l" else 3) if side else (
            1 if any(part in name for part in ("Head", "Hat", "Skull", "Helmet", "Eyes", "Jaw")) else 0)
        for tri in mesh.loop_triangles:
            for loop, vertex in zip(tri.loops, tri.vertices):
                point = world @ mesh.vertices[vertex].co
                normal = (normals @ mesh.corner_normals[loop].vector).normalized()
                rows.append((point, normal, bone, uvs[loop].uv.copy()))
        evaluated_obj.to_mesh_clear()

    bottom = min(row[0].z for row in rows)
    height = max(row[0].z for row in rows) - bottom
    scale = 2.8 / height
    pivots = [Vector(), rig.matrix_world @ rig.pose.bones["head"].head, shoulders["l"], shoulders["r"]]
    data = array.array("f")
    for pivot in pivots + [Vector()] * 5:
        data.extend((pivot.x * scale, pivot.y * scale, (pivot.z - bottom) * scale))
    for point, normal, bone, uv in rows:
        data.extend((point.x * scale, point.y * scale, (point.z - bottom) * scale,
                     *normal, 1., 1., 1., bone, *uv))
    if sys.byteorder != "little":
        data.byteswap()
    (OUT / f"creature-{slug}.mesh").write_bytes(data.tobytes())
    print(slug, len(rows) // 3, "triangles", "source height", round(height, 3))


with TemporaryDirectory(prefix="kaykit-arsenal-") as scratch:
    for model in MODELS:
        export_model(*model, Path(scratch))
