(function (root, factory) {
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    root.CurveMath = api;
}(typeof globalThis !== "undefined" ? globalThis : this, function () {
    function findMaxSlopeSegment(rows, forceIndex, displacementIndex) {
        if (!Array.isArray(rows) || rows.length < 2) throw new Error("至少需要两个采样点");
        let selected = null;
        let maxSlope = -Infinity;
        for (let index = 1; index < rows.length; index++) {
            const previous = rows[index - 1];
            const current = rows[index];
            const forceDelta = Number(current[forceIndex]) - Number(previous[forceIndex]);
            if (Math.abs(forceDelta) < Number.EPSILON) continue;
            const displacementDelta = Number(current[displacementIndex]) - Number(previous[displacementIndex]);
            const slope = Math.abs(displacementDelta / forceDelta);
            if (slope > maxSlope) {
                maxSlope = slope;
                selected = [
                    [Number(previous[forceIndex]), Number(previous[displacementIndex])],
                    [Number(current[forceIndex]), Number(current[displacementIndex])]
                ];
            }
        }
        if (!selected) throw new Error("相邻采样点的受力值不能全部相同");
        return selected;
    }

    function quantizeDisplacement(value) {
        return Math.round(Number(value) * 100) / 100;
    }

    function lineIntercept(points) {
        const [[x1, y1], [x2, y2]] = points;
        if (Math.abs(y2 - y1) < Number.EPSILON) return x1;
        return x1 - y1 * (x2 - x1) / (y2 - y1);
    }

    function anchorSegment(points, maximumDisplacement) {
        if (!(Number(maximumDisplacement) > 0)) throw new Error("最高位移必须大于 0");
        const [[x1, y1], [x2, y2]] = points;
        if (Math.abs(y2 - y1) < Number.EPSILON) throw new Error("拟合线端点位移不能相同");
        const upperX = x1 + (Number(maximumDisplacement) - y1) * (x2 - x1) / (y2 - y1);
        return [[lineIntercept(points), 0], [upperX, Number(maximumDisplacement)]];
    }

    function meanPoints(down, up) {
        return [
            [(down[0][0] + up[0][0]) / 2, (down[0][1] + up[0][1]) / 2],
            [(down[1][0] + up[1][0]) / 2, (down[1][1] + up[1][1]) / 2]
        ];
    }

    function buildSmoothPath(points) {
        if (!points.length) return "";
        const clean = value => Math.round(value * 100) / 100;
        const path = [`M ${clean(points[0][0])} ${clean(points[0][1])}`];
        for (let index = 0; index < points.length - 1; index++) {
            const previous = points[index - 1] || points[index];
            const current = points[index];
            const next = points[index + 1];
            const following = points[index + 2] || next;
            const minY = Math.min(current[1], next[1]);
            const maxY = Math.max(current[1], next[1]);
            const control1 = [
                current[0] + (next[0] - previous[0]) / 6,
                Math.max(minY, Math.min(maxY, current[1] + (next[1] - previous[1]) / 6))
            ];
            const control2 = [
                next[0] - (following[0] - current[0]) / 6,
                Math.max(minY, Math.min(maxY, next[1] - (following[1] - current[1]) / 6))
            ];
            path.push(`C ${clean(control1[0])} ${clean(control1[1])}, ${clean(control2[0])} ${clean(control2[1])}, ${clean(next[0])} ${clean(next[1])}`);
        }
        return path.join(" ");
    }

    return { findMaxSlopeSegment, quantizeDisplacement, lineIntercept, anchorSegment, meanPoints, buildSmoothPath };
}));

