/*
 * 金标准向量生成器
 *
 * 用 csTimer 原始 JS 生成金标准数据，供 Kotlin 移植版做一致性校验：
 *   1) ISAAC 随机数向量（种子 -> 连续 random() 值）      -> isaac_golden*.txt
 *   2) 333 打乱“目标态”（送入求解器前的 facelet 串）      -> scramble333_state_golden*.txt
 *
 * 运行：node tools/gen-golden.js
 *
 * 为何只 golden 打乱的“目标态”而不是最终打乱字符串：
 *   csTimer 的 333 打乱由 min2phase 求解随机态取逆解得到。`Util` 剪枝表是
 *   全局共享且**增量**初始化的，同一个态在表的不同初始化进度下会返回**不同但等价**
 *   的最优解——即最终字符串依赖求解器内部状态（进而依赖调用顺序），不是一个稳定契约。
 *   而“目标态”在调用求解器之前就已确定，与求解器状态无关，且可由
 *   `CubeState.faceletsOf(打乱串)` 独立复算，是最稳健的黄金基准。
 *
 * csTimer JS 来源：本仓库 git 历史中最后一次包含它的修订（见 CSTIMER_REV），
 * 目录 `tools/cstimer/` 已被 .gitignore 忽略；若缺失，脚本会自动从 git 取回，
 * 因此无需联网、也无需把 GPL 源码提交进仓库。
 */
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const { execSync } = require("child_process");

const ROOT = path.resolve(__dirname, "..");
const JS_DIR = path.join(ROOT, "tools", "cstimer", "js");

// 最后一次仍包含 csTimer JS 的提交（其后这些文件被删除）。
const CSTIMER_REV = "b35a91e5d6099d1981755378edcb3cf19e1e4833";
const CSTIMER_SRC = "app/src/main/assets/www/js/";
const JS_FILES = [
    "lib/isaac.js",
    "lib/mathlib.js",
    "lib/min2phase.js",
    "scramble/scramble_333_edit.js",
];

const SCRAMBLE_COUNT = 50;
const RANDOM_COUNT = 20;

/** 若本地缺少 csTimer JS，则从 git 历史取回（不联网、不提交）。 */
function ensureCsTimerJs() {
    for (const rel of JS_FILES) {
        const dest = path.join(JS_DIR, rel);
        if (fs.existsSync(dest)) continue;
        const buf = execSync("git show " + CSTIMER_REV + ":" + CSTIMER_SRC + rel, {
            cwd: ROOT,
            maxBuffer: 1e8,
        });
        fs.mkdirSync(path.dirname(dest), { recursive: true });
        fs.writeFileSync(dest, buf);
        console.log("restored " + dest + " from " + CSTIMER_REV);
    }
}

const JS = (p) => path.join(JS_DIR, p);

// scrMgr shim：仅需支持链式 reg()，其余函数不会在 333 路径上被调用
const scrMgr = {
    reg: function reg() {
        return reg;
    },
    scramblers: {},
    fixCase: function () {
        return 0;
    },
    toTxt: function (x) {
        return x;
    },
};

// 每个种子重新采集：注入 __DUMP__，记录送入求解器前的目标态。
let dumps = [];

/** 新建一个干净的 csTimer 运行环境（重新加载全部 JS） */
function createContext() {
    const sandbox = {
        module: { exports: {} },
        exports: {},
        console: console,
        Math: Math,
        Date: Date,
        JSON: JSON,
        DEBUG: false,
        scrMgr: scrMgr,
        crypto: require("crypto").webcrypto,
    };
    sandbox.globalThis = sandbox;
    sandbox.__DUMP__ = (o) => dumps.push(o);
    vm.createContext(sandbox);
    for (const rel of JS_FILES) {
        let src = fs.readFileSync(JS(rel), "utf8");
        if (rel === "scramble/scramble_333_edit.js") {
            src = src.replace(
                /var posit = cc\.toFaceCube\(\);/,
                "var posit = cc.toFaceCube();" +
                    " if (typeof __DUMP__ === 'function') __DUMP__({posit: posit});"
            );
        }
        vm.runInContext(src, sandbox, { filename: rel });
    }
    return sandbox;
}

/** 生成 256 个 uint16 的确定性种子（LCG），模拟 crypto.getRandomValues(Uint16Array(256)) */
function lcgSeed(mult, inc, init) {
    const out = [];
    let s = init >>> 0;
    for (let i = 0; i < 256; i++) {
        s = (Math.imul(s, mult) + inc) >>> 0;
        out.push(s & 0xffff);
    }
    return out;
}

// 种子集合：第一个为既有默认种子（保持与既有金标准逐字节一致），其余为边缘/补充种子。
const SEEDS = [
    ["", lcgSeed(1103515245, 12345, 0x12345678)],
    ["_zeros", new Array(256).fill(0)],
    ["_ones", new Array(256).fill(0xffff)],
    ["_sparse", Array.from({ length: 256 }, (_, i) => (i === 255 ? 1 : 0))],
];

function writeResource(name, content) {
    const dest = path.join(ROOT, "app", "src", "test", "resources", name);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, content, "utf8");
    console.log("wrote " + dest);
}

function runSeed(suffix, seed) {
    const seedStr = String.fromCharCode.apply(null, seed);

    // 1) ISAAC 随机数向量（纯函数，无全局状态）
    {
        const sb = createContext();
        sb.mathlib.setSeed(256, seedStr); // isaac.seed + 预推进 256 次
        const vals = [];
        for (let i = 0; i < RANDOM_COUNT; i++) {
            vals.push(sb.isaac.random());
        }
        writeResource(
            "isaac_golden" + suffix + ".txt",
            ["SEED " + seed.join(","), ...vals.map(String)].join("\n") + "\n"
        );
    }

    // 2) 333 打乱目标态向量（送入求解器前的 facelet 串）
    {
        dumps = [];
        const sb = createContext();
        sb.mathlib.setSeed(256, seedStr);
        for (let i = 0; i < SCRAMBLE_COUNT; i++) {
            sb.scramble_333.getRandomScramble();
        }
        if (dumps.length !== SCRAMBLE_COUNT) {
            throw new Error(
                "目标态数量与打乱数量不符: " + dumps.length + " != " + SCRAMBLE_COUNT
            );
        }
        writeResource(
            "scramble333_state_golden" + suffix + ".txt",
            ["SEED " + seed.join(","), ...dumps.map((d) => d.posit)].join("\n") + "\n"
        );
    }
}

ensureCsTimerJs();
for (const [suffix, seed] of SEEDS) {
    console.log("seed \"" + (suffix || "default") + "\"");
    runSeed(suffix, seed);
}
