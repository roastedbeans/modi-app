"""
QMDL Log Analyzer
Python module for analyzing Qualcomm Mobile Diagnostic logs
"""

import os
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from datetime import datetime
import json
import re

class QmdlAnalyzer:
    """Main class for QMDL log analysis"""

    def __init__(self):
        self.log_data = []
        self.analysis_results = {}

    def parse_log_file(self, file_path):
        """Parse a QMDL log file and extract relevant data"""
        try:
            if not os.path.exists(file_path):
                return {"error": f"File not found: {file_path}"}

            with open(file_path, 'r', encoding='utf-8', errors='ignore') as file:
                lines = file.readlines()

            # Parse different types of log entries
            parsed_data = {
                'timestamp': [],
                'log_level': [],
                'component': [],
                'message': [],
                'raw_data': []
            }

            timestamp_pattern = r'(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})'
            log_pattern = r'(\w+)/(\w+):\s*(.+)'

            for line in lines:
                line = line.strip()
                if not line:
                    continue

                # Extract timestamp
                timestamp_match = re.search(timestamp_pattern, line)
                timestamp = timestamp_match.group(1) if timestamp_match else None

                # Extract log components
                log_match = re.search(log_pattern, line)

                if log_match:
                    component = log_match.group(1)
                    log_level = log_match.group(2)
                    message = log_match.group(3)
                else:
                    component = 'UNKNOWN'
                    log_level = 'INFO'
                    message = line

                parsed_data['timestamp'].append(timestamp)
                parsed_data['log_level'].append(log_level)
                parsed_data['component'].append(component)
                parsed_data['message'].append(message)
                parsed_data['raw_data'].append(line)

            # Create DataFrame
            df = pd.DataFrame(parsed_data)

            # Basic analysis
            analysis = {
                'total_entries': len(df),
                'unique_components': df['component'].nunique(),
                'log_level_counts': df['log_level'].value_counts().to_dict(),
                'component_counts': df['component'].value_counts().to_dict(),
                'time_range': {
                    'start': df['timestamp'].min() if df['timestamp'].any() else None,
                    'end': df['timestamp'].max() if df['timestamp'].any() else None
                }
            }

            self.analysis_results[file_path] = {
                'data': df,
                'analysis': analysis
            }

            return {
                'success': True,
                'file': file_path,
                'analysis': analysis
            }

        except Exception as e:
            return {
                'error': f'Failed to parse log file: {str(e)}'
            }

    def analyze_log_directory(self, directory_path):
        """Analyze all log files in a directory"""
        try:
            if not os.path.exists(directory_path):
                return {"error": f"Directory not found: {directory_path}"}

            log_files = [f for f in os.listdir(directory_path)
                        if f.endswith(('.log', '.txt', '.qmdl'))]

            results = {}
            for log_file in log_files:
                file_path = os.path.join(directory_path, log_file)
                results[log_file] = self.parse_log_file(file_path)

            return {
                'success': True,
                'directory': directory_path,
                'files_analyzed': len(log_files),
                'results': results
            }

        except Exception as e:
            return {
                'error': f'Failed to analyze directory: {str(e)}'
            }

    def generate_report(self, output_path=None):
        """Generate a comprehensive analysis report"""
        try:
            if not self.analysis_results:
                return {"error": "No analysis data available"}

            report = {
                'generated_at': datetime.now().isoformat(),
                'files_analyzed': len(self.analysis_results),
                'summary': {
                    'total_entries': 0,
                    'components': set(),
                    'log_levels': set()
                }
            }

            for file_path, result in self.analysis_results.items():
                if 'analysis' in result:
                    analysis = result['analysis']
                    report['summary']['total_entries'] += analysis['total_entries']
                    report['summary']['components'].update(analysis['component_counts'].keys())
                    report['summary']['log_levels'].update(analysis['log_level_counts'].keys())

            # Convert sets to lists for JSON serialization
            report['summary']['components'] = list(report['summary']['components'])
            report['summary']['log_levels'] = list(report['summary']['log_levels'])

            if output_path:
                with open(output_path, 'w') as f:
                    json.dump(report, f, indent=2)

            return {
                'success': True,
                'report': report
            }

        except Exception as e:
            return {
                'error': f'Failed to generate report: {str(e)}'
            }

    def extract_error_patterns(self):
        """Extract common error patterns from logs"""
        try:
            error_patterns = []

            for file_path, result in self.analysis_results.items():
                if 'data' in result:
                    df = result['data']

                    # Look for error-related entries
                    error_df = df[df['log_level'].isin(['ERROR', 'FATAL', 'CRASH'])]

                    for _, row in error_df.iterrows():
                        error_patterns.append({
                            'file': os.path.basename(file_path),
                            'timestamp': row['timestamp'],
                            'component': row['component'],
                            'message': row['message']
                        })

            return {
                'success': True,
                'error_count': len(error_patterns),
                'patterns': error_patterns
            }

        except Exception as e:
            return {
                'error': f'Failed to extract error patterns: {str(e)}'
            }
