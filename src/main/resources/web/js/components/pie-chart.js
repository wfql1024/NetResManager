/**
 * pie-chart.js - Pie chart rendering using Chart.js (with fallback).
 */
NRM.components = NRM.components || {};

NRM.components.pieChart = (function() {
    'use strict';

    var charts = {};

    function render(canvasId, data, title, bySize) {
        var canvas = document.getElementById(canvasId);
        if (!canvas) {
            console.warn('Canvas not found: ' + canvasId);
            return;
        }

        // Destroy existing chart
        if (charts[canvasId]) {
            try { charts[canvasId].destroy(); } catch(e) {}
            delete charts[canvasId];
        }

        var ctx = canvas.getContext('2d');
        if (!ctx) return;

        // Ensure canvas has proper size
        canvas.width = canvas.parentElement ? canvas.parentElement.clientWidth - 32 : 300;
        canvas.height = 260;

        // Empty data fallback
        if (!data || data.length === 0) {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.font = '14px "Microsoft YaHei", sans-serif';
            ctx.fillStyle = '#999';
            ctx.textAlign = 'center';
            ctx.fillText('暂无数据', canvas.width / 2, canvas.height / 2);
            return;
        }

        // Check if Chart is available
        if (typeof Chart === 'undefined') {
            console.warn('Chart.js not loaded, drawing fallback');
            // Draw a simple text-based fallback
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.font = '12px "Microsoft YaHei", sans-serif';
            ctx.textAlign = 'center';
            var y = 30;
            ctx.fillStyle = '#333';
            ctx.font = 'bold 13px "Microsoft YaHei", sans-serif';
            ctx.fillText(title || '统计', canvas.width / 2, y);
            ctx.font = '11px "Microsoft YaHei", sans-serif';
            y += 24;
            data.forEach(function(d, i) {
                ctx.fillStyle = generateColors(1)[0];
                ctx.fillText(d.category + ': ' + d.count + ' 个',
                    canvas.width / 2, y);
                y += 18;
                if (y > canvas.height - 10) return;
            });
            return;
        }

        // Determine values to chart
        var useSize = bySize;
        if (useSize) {
            var hasAnySize = false;
            for (var i = 0; i < data.length; i++) {
                if (data[i].totalSize && data[i].totalSize > 0) {
                    hasAnySize = true;
                    break;
                }
            }
            if (!hasAnySize) useSize = false;
        }

        var labels = data.map(function(d) { return d.category; });
        var values = data.map(function(d) { return useSize ? d.totalSize : d.count; });
        var colors = generateColors(data.length);

        try {
            charts[canvasId] = new Chart(canvas, {
                type: 'pie',
                data: {
                    labels: labels,
                    datasets: [{
                        data: values,
                        backgroundColor: colors,
                        borderColor: '#fff',
                        borderWidth: 1
                    }]
                },
                options: {
                    responsive: false,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            position: 'bottom',
                            labels: {
                                font: { size: 10 },
                                padding: 8,
                                boxWidth: 10,
                                generateLabels: function(chart) {
                                    var ds = chart.data.datasets[0];
                                    return chart.data.labels.map(function(label, i) {
                                        return {
                                            text: label + ' (' + ds.data[i] + ')',
                                            fillStyle: ds.backgroundColor[i],
                                            hidden: false,
                                            index: i
                                        };
                                    });
                                }
                            }
                        }
                    }
                }
            });
        } catch(e) {
            console.error('Chart creation failed: ' + e.message);
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.font = '14px "Microsoft YaHei", sans-serif';
            ctx.fillStyle = '#999';
            ctx.textAlign = 'center';
            ctx.fillText('图表加载失败', canvas.width / 2, canvas.height / 2);
        }
    }

    function generateColors(count) {
        var baseColors = [
            '#0078d4', '#107c10', '#d13438', '#ff8f00',
            '#886ce4', '#0099bc', '#e74856', '#7a7574',
            '#038387', '#8764b8', '#00b7c3', '#ca5010',
            '#498205', '#c239b3', '#004e8c', '#6b2929'
        ];
        var colors = [];
        for (var i = 0; i < count; i++) {
            colors.push(baseColors[i % baseColors.length]);
        }
        return colors;
    }

    return { render: render };
})();
