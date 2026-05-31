/**
 * pie-chart.js - Pie chart rendering using Chart.js.
 */
NRM.components = NRM.components || {};

NRM.components.pieChart = (function() {
    'use strict';

    var charts = {};

    function render(canvasId, data, title, bySize) {
        var canvas = document.getElementById(canvasId);
        if (!canvas) return;

        // Destroy previous chart
        if (charts[canvasId]) {
            try { charts[canvasId].destroy(); } catch(e) {}
            delete charts[canvasId];
        }

        var ctx = canvas.getContext('2d');
        if (!ctx) return;

        // Set explicit canvas pixel size (for sharp rendering)
        var size = 280;
        canvas.width = size;
        canvas.height = size;
        canvas.style.maxWidth = '100%';
        canvas.style.height = 'auto';

        // Empty data
        if (!data || data.length === 0) {
            ctx.font = '14px "Microsoft YaHei", sans-serif';
            ctx.fillStyle = '#999';
            ctx.textAlign = 'center';
            ctx.fillText('暂无数据', size/2, size/2);
            return;
        }

        // Chart.js not loaded fallback
        if (typeof Chart === 'undefined') {
            ctx.font = '12px "Microsoft YaHei", sans-serif';
            ctx.fillStyle = '#333';
            ctx.textAlign = 'center';
            var y = 30;
            data.forEach(function(d) {
                ctx.fillText(d.category + ': ' + d.count + ' 个', size/2, y);
                y += 18;
                if (y > size - 10) return;
            });
            return;
        }

        // Decide value type
        var useSize = bySize;
        if (useSize) {
            var hasSize = false;
            for (var i = 0; i < data.length; i++) {
                if (data[i].totalSize && data[i].totalSize > 0) { hasSize = true; break; }
            }
            if (!hasSize) useSize = false;
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
                    maintainAspectRatio: true,
                    plugins: {
                        legend: {
                            position: 'bottom',
                            labels: {
                                font: { size: 10 },
                                padding: 8,
                                boxWidth: 10,
                                generateLabels: function(chart) {
                                    return chart.data.labels.map(function(label, i) {
                                        return {
                                            text: label,
                                            fillStyle: chart.data.datasets[0].backgroundColor[i],
                                            hidden: false,
                                            index: i
                                        };
                                    });
                                }
                            }
                        },
                        tooltip: {
                            callbacks: {
                                label: function(ctx) {
                                    var item = data[ctx.dataIndex];
                                    return item.category + ': ' + item.totalSizeFormatted + ' (' + item.count + ' 个)';
                                }
                            }
                        }
                    }
                }
            });
        } catch(e) {
            console.error('Chart error: ' + e.message);
            ctx.font = '14px "Microsoft YaHei", sans-serif';
            ctx.fillStyle = '#999';
            ctx.textAlign = 'center';
            ctx.fillText('Chart Error', size/2, size/2);
        }
    }

    function generateColors(count) {
        var base = ['#0078d4','#107c10','#d13438','#ff8f00','#886ce4','#0099bc',
                    '#e74856','#7a7574','#038387','#8764b8','#00b7c3','#ca5010',
                    '#498205','#c239b3','#004e8c','#6b2929'];
        var c = [];
        for (var i = 0; i < count; i++) c.push(base[i % base.length]);
        return c;
    }

    return { render: render };
})();
