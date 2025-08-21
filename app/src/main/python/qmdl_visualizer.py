"""
QMDL Data Visualizer
Python module for creating visualizations from QMDL analysis data
"""

import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
import os
from datetime import datetime

class QmdlVisualizer:
    """Class for creating visualizations from QMDL data"""

    def __init__(self):
        self.output_dir = "/sdcard/diag_logs/visualizations"
        os.makedirs(self.output_dir, exist_ok=True)

    def create_component_distribution_chart(self, analysis_results, output_filename="component_distribution.png"):
        """Create a pie chart showing component distribution"""
        try:
            if not analysis_results:
                return {"error": "No analysis results provided"}

            # Aggregate component counts across all files
            total_components = {}
            for result in analysis_results.values():
                if 'analysis' in result and 'component_counts' in result['analysis']:
                    for component, count in result['analysis']['component_counts'].items():
                        total_components[component] = total_components.get(component, 0) + count

            if not total_components:
                return {"error": "No component data found"}

            # Create pie chart
            plt.figure(figsize=(10, 8))
            plt.pie(total_components.values(), labels=total_components.keys(), autopct='%1.1f%%')
            plt.title('Log Component Distribution')
            plt.axis('equal')

            output_path = os.path.join(self.output_dir, output_filename)
            plt.savefig(output_path, dpi=300, bbox_inches='tight')
            plt.close()

            return {
                'success': True,
                'output_path': output_path,
                'components': total_components
            }

        except Exception as e:
            return {
                'error': f'Failed to create component chart: {str(e)}'
            }

    def create_timeline_chart(self, log_data, output_filename="timeline_analysis.png"):
        """Create a timeline showing log activity over time"""
        try:
            if log_data.empty:
                return {"error": "No log data provided"}

            # Convert timestamps to datetime
            log_data['timestamp'] = pd.to_datetime(log_data['timestamp'], errors='coerce')

            # Remove rows with invalid timestamps
            log_data = log_data.dropna(subset=['timestamp'])

            if log_data.empty:
                return {"error": "No valid timestamps found"}

            # Group by time intervals (e.g., per minute)
            log_data['time_group'] = log_data['timestamp'].dt.floor('T')  # Floor to minute
            timeline_data = log_data.groupby('time_group').size()

            plt.figure(figsize=(12, 6))
            plt.plot(timeline_data.index, timeline_data.values, marker='o', linestyle='-')
            plt.title('Log Activity Timeline')
            plt.xlabel('Time')
            plt.ylabel('Number of Log Entries')
            plt.xticks(rotation=45)
            plt.grid(True, alpha=0.3)
            plt.tight_layout()

            output_path = os.path.join(self.output_dir, output_filename)
            plt.savefig(output_path, dpi=300, bbox_inches='tight')
            plt.close()

            return {
                'success': True,
                'output_path': output_path,
                'time_range': {
                    'start': str(timeline_data.index.min()),
                    'end': str(timeline_data.index.max())
                }
            }

        except Exception as e:
            return {
                'error': f'Failed to create timeline chart: {str(e)}'
            }

    def create_error_analysis_chart(self, error_patterns, output_filename="error_analysis.png"):
        """Create a chart analyzing error patterns"""
        try:
            if not error_patterns:
                return {"error": "No error patterns provided"}

            # Convert to DataFrame for easier analysis
            error_df = pd.DataFrame(error_patterns)

            if error_df.empty:
                return {"error": "Empty error data"}

            # Count errors by component
            component_errors = error_df['component'].value_counts()

            plt.figure(figsize=(10, 6))
            bars = plt.bar(range(len(component_errors)), component_errors.values)
            plt.xlabel('Components')
            plt.ylabel('Number of Errors')
            plt.title('Error Distribution by Component')
            plt.xticks(range(len(component_errors)), component_errors.index, rotation=45)

            # Add value labels on bars
            for bar, value in zip(bars, component_errors.values):
                plt.text(bar.get_x() + bar.get_width()/2, bar.get_height(),
                        str(value), ha='center', va='bottom')

            plt.tight_layout()

            output_path = os.path.join(self.output_dir, output_filename)
            plt.savefig(output_path, dpi=300, bbox_inches='tight')
            plt.close()

            return {
                'success': True,
                'output_path': output_path,
                'error_summary': component_errors.to_dict()
            }

        except Exception as e:
            return {
                'error': f'Failed to create error analysis chart: {str(e)}'
            }

    def create_log_level_distribution(self, analysis_results, output_filename="log_level_distribution.png"):
        """Create a bar chart showing log level distribution"""
        try:
            if not analysis_results:
                return {"error": "No analysis results provided"}

            # Aggregate log level counts across all files
            total_log_levels = {}
            for result in analysis_results.values():
                if 'analysis' in result and 'log_level_counts' in result['analysis']:
                    for level, count in result['analysis']['log_level_counts'].items():
                        total_log_levels[level] = total_log_levels.get(level, 0) + count

            if not total_log_levels:
                return {"error": "No log level data found"}

            plt.figure(figsize=(8, 6))
            colors = {'ERROR': 'red', 'WARN': 'orange', 'INFO': 'blue', 'DEBUG': 'green', 'FATAL': 'darkred', 'CRASH': 'purple'}

            bars = plt.bar(total_log_levels.keys(), total_log_levels.values(),
                          color=[colors.get(level, 'gray') for level in total_log_levels.keys()])

            plt.xlabel('Log Level')
            plt.ylabel('Count')
            plt.title('Log Level Distribution')
            plt.grid(True, alpha=0.3)

            # Add value labels on bars
            for bar, value in zip(bars, total_log_levels.values):
                plt.text(bar.get_x() + bar.get_width()/2, bar.get_height(),
                        str(value), ha='center', va='bottom')

            plt.tight_layout()

            output_path = os.path.join(self.output_dir, output_filename)
            plt.savefig(output_path, dpi=300, bbox_inches='tight')
            plt.close()

            return {
                'success': True,
                'output_path': output_path,
                'log_levels': total_log_levels
            }

        except Exception as e:
            return {
                'error': f'Failed to create log level chart: {str(e)}'
            }
