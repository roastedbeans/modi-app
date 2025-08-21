"""
QMDL Machine Learning Analyzer
Python module for ML-based analysis of QMDL logs
"""

import pandas as pd
import numpy as np
import re
import json
import random
from datetime import datetime, timedelta

class QmdlMlAnalyzer:
    """Machine learning analyzer for QMDL logs"""

    def __init__(self):
        self.stop_words = set(['the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by'])

    def detect_anomalies(self, log_data):
        """Detect anomalous log entries using simple heuristics"""
        try:
            if log_data.empty:
                return {"error": "No log data provided"}

            # Simple anomaly detection based on patterns
            anomalies = []

            for i, row in log_data.iterrows():
                anomaly_score = 0
                reasons = []

                # Check message length (very short or very long messages might be anomalous)
                message = str(row.get('message', ''))
                message_length = len(message)
                if message_length < 5:
                    anomaly_score += 0.3
                    reasons.append('very_short_message')
                elif message_length > 500:
                    anomaly_score += 0.2
                    reasons.append('very_long_message')

                # Check for error levels
                log_level = str(row.get('log_level', '')).upper()
                if log_level in ['FATAL', 'CRASH']:
                    anomaly_score += 0.5
                    reasons.append('critical_error')

                # Check for unusual characters or patterns
                if re.search(r'[^\x20-\x7E]', message):
                    anomaly_score += 0.2
                    reasons.append('non_ascii_chars')

                # Check for repeated characters
                if re.search(r'(.)\1{10,}', message):
                    anomaly_score += 0.3
                    reasons.append('repeated_chars')

                # If anomaly score is high enough, mark as anomaly
                if anomaly_score >= 0.4:
                    anomalies.append({
                        'index': i,
                        'timestamp': str(row.get('timestamp')),
                        'component': row.get('component'),
                        'log_level': row.get('log_level'),
                        'message': row.get('message'),
                        'anomaly_score': anomaly_score,
                        'reasons': reasons
                    })

            return {
                'success': True,
                'anomaly_count': len(anomalies),
                'total_entries': len(log_data),
                'anomaly_percentage': (len(anomalies) / len(log_data)) * 100,
                'anomalies': anomalies
            }

        except Exception as e:
            return {
                'error': f'Failed to detect anomalies: {str(e)}'
            }

    def cluster_log_messages(self, log_data, n_clusters=5):
        """Cluster log messages using simple keyword-based clustering"""
        try:
            if log_data.empty:
                return {"error": "No log data provided"}

            # Extract messages for clustering
            messages = log_data['message'].fillna('').astype(str).tolist()

            if not messages:
                return {"error": "No messages found"}

            # Simple keyword-based clustering
            cluster_keywords = {
                0: ['error', 'failed', 'failure', 'exception'],
                1: ['warning', 'warn', 'deprecated'],
                2: ['info', 'information', 'start', 'begin'],
                3: ['debug', 'trace', 'verbose'],
                4: ['network', 'connection', 'socket', 'http'],
                5: ['memory', 'allocation', 'heap', 'gc'],
                6: ['file', 'io', 'read', 'write'],
                7: ['thread', 'process', 'cpu', 'performance']
            }

            # Assign messages to clusters
            clusters = {}
            for i, message in enumerate(messages):
                message_lower = message.lower()
                assigned_cluster = None
                max_matches = 0

                # Find best matching cluster
                for cluster_id, keywords in cluster_keywords.items():
                    matches = sum(1 for keyword in keywords if keyword in message_lower)
                    if matches > max_matches:
                        max_matches = matches
                        assigned_cluster = cluster_id

                # If no matches, assign to "other" cluster
                if assigned_cluster is None or max_matches == 0:
                    assigned_cluster = 99

                if str(assigned_cluster) not in clusters:
                    clusters[str(assigned_cluster)] = []

                clusters[str(assigned_cluster)].append({
                    'message': message,
                    'component': log_data.iloc[i].get('component', 'UNKNOWN'),
                    'log_level': log_data.iloc[i].get('log_level', 'INFO'),
                    'timestamp': str(log_data.iloc[i].get('timestamp'))
                })

            # Create cluster info
            cluster_info = {}
            for cluster_id, cluster_messages in clusters.items():
                if cluster_id == '99':
                    cluster_name = 'Other'
                else:
                    cluster_name = f"Cluster {cluster_id}: {', '.join(cluster_keywords.get(int(cluster_id), ['unknown']))}"

                cluster_info[cluster_id] = {
                    'size': len(cluster_messages),
                    'name': cluster_name,
                    'most_common_component': max([item['component'] for item in cluster_messages],
                                               key=[item['component'] for item in cluster_messages].count),
                    'sample_messages': [item['message'] for item in cluster_messages[:5]],
                    'messages': cluster_messages
                }

            return {
                'success': True,
                'n_clusters': len(clusters),
                'cluster_info': cluster_info
            }

        except Exception as e:
            return {
                'error': f'Failed to cluster messages: {str(e)}'
            }

    def analyze_error_patterns(self, log_data):
        """Analyze patterns in error messages"""
        try:
            if log_data.empty:
                return {"error": "No log data provided"}

            # Filter error messages
            error_data = log_data[log_data['log_level'].isin(['ERROR', 'FATAL', 'CRASH'])]

            if error_data.empty:
                return {
                    'success': True,
                    'message': 'No error messages found',
                    'error_count': 0
                }

            # Extract patterns from error messages
            error_messages = error_data['message'].fillna('').astype(str).tolist()

            # Common error patterns
            patterns = {
                'null_pointer': r'null\s+pointer|NullPointerException',
                'out_of_memory': r'out\s+of\s+memory|OutOfMemoryError',
                'timeout': r'timeout|timed\s+out',
                'permission_denied': r'permission\s+denied|access\s+denied',
                'network_error': r'network\s+error|connection\s+(failed|refused)',
                'file_not_found': r'file\s+not\s+found|No\s+such\s+file',
                'database_error': r'database\s+error|SQL\s+error'
            }

            pattern_analysis = {}
            for pattern_name, pattern_regex in patterns.items():
                matches = []
                for i, message in enumerate(error_messages):
                    if re.search(pattern_regex, message, re.IGNORECASE):
                        matches.append({
                            'index': i,
                            'message': message,
                            'timestamp': str(error_data.iloc[i].get('timestamp')),
                            'component': error_data.iloc[i].get('component')
                        })

                pattern_analysis[pattern_name] = {
                    'count': len(matches),
                    'matches': matches
                }

            # Sort patterns by frequency
            sorted_patterns = sorted(pattern_analysis.items(),
                                   key=lambda x: x[1]['count'],
                                   reverse=True)

            return {
                'success': True,
                'error_count': len(error_messages),
                'pattern_analysis': dict(sorted_patterns)
            }

        except Exception as e:
            return {
                'error': f'Failed to analyze error patterns: {str(e)}'
            }

    def predict_error_likelihood(self, log_data):
        """Predict likelihood of errors based on recent log patterns"""
        try:
            if log_data.empty:
                return {"error": "No log data provided"}

            # Convert timestamps to datetime for time-based analysis
            log_data['timestamp'] = pd.to_datetime(log_data['timestamp'], errors='coerce')
            log_data = log_data.dropna(subset=['timestamp'])

            if log_data.empty:
                return {"error": "No valid timestamps found"}

            # Sort by timestamp
            log_data = log_data.sort_values('timestamp')

            # Analyze error patterns over time
            log_data['is_error'] = log_data['log_level'].isin(['ERROR', 'FATAL', 'CRASH']).astype(int)

            # Calculate rolling error rate (last 10 entries)
            log_data['error_rate_10'] = log_data['is_error'].rolling(window=10, min_periods=1).mean()

            # Current error rate
            current_error_rate = log_data['error_rate_10'].iloc[-1] if len(log_data) > 0 else 0

            # Recent trends
            recent_trend = "stable"
            if len(log_data) >= 20:
                first_half = log_data['error_rate_10'].iloc[-20:-10].mean()
                second_half = log_data['error_rate_10'].iloc[-10:].mean()

                if second_half > first_half * 1.5:
                    recent_trend = "increasing"
                elif second_half < first_half * 0.7:
                    recent_trend = "decreasing"

            # Risk assessment
            if current_error_rate > 0.3:
                risk_level = "high"
            elif current_error_rate > 0.1:
                risk_level = "medium"
            else:
                risk_level = "low"

            return {
                'success': True,
                'current_error_rate': float(current_error_rate),
                'recent_trend': recent_trend,
                'risk_level': risk_level,
                'analysis_window': len(log_data),
                'recommendations': self._generate_recommendations(risk_level, recent_trend)
            }

        except Exception as e:
            return {
                'error': f'Failed to predict error likelihood: {str(e)}'
            }

    def _generate_recommendations(self, risk_level, trend):
        """Generate recommendations based on risk assessment"""
        recommendations = []

        if risk_level == "high":
            recommendations.extend([
                "Immediate attention required",
                "Check system resources and memory usage",
                "Review recent configuration changes",
                "Consider restarting problematic services"
            ])
        elif risk_level == "medium":
            recommendations.extend([
                "Monitor system closely",
                "Check for unusual patterns in recent logs",
                "Verify system resource utilization"
            ])
        else:
            recommendations.extend([
                "System appears stable",
                "Continue normal monitoring"
            ])

        if trend == "increasing":
            recommendations.append("Error rate is increasing - investigate potential issues")

        return recommendations
