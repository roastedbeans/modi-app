"""
QMDL Python-Java Bridge
Interface module between Java application and Python analysis modules
"""

import json
import os
from qmdl_analyzer import QmdlAnalyzer
from qmdl_visualizer import QmdlVisualizer
from qmdl_ml_analyzer import QmdlMlAnalyzer

class QmdlBridge:
    """Bridge class for Java-Python communication"""

    def __init__(self):
        self.analyzer = QmdlAnalyzer()
        self.visualizer = QmdlVisualizer()
        self.ml_analyzer = QmdlMlAnalyzer()

    def analyze_log_file(self, file_path):
        """Analyze a single log file"""
        try:
            result = self.analyzer.parse_log_file(file_path)
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def analyze_log_directory(self, directory_path):
        """Analyze all log files in a directory"""
        try:
            result = self.analyzer.analyze_log_directory(directory_path)
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def generate_analysis_report(self, output_path=None):
        """Generate a comprehensive analysis report"""
        try:
            result = self.analyzer.generate_report(output_path)
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def extract_error_patterns(self):
        """Extract error patterns from analyzed logs"""
        try:
            result = self.analyzer.extract_error_patterns()
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def create_component_chart(self, output_filename="component_distribution.png"):
        """Create component distribution visualization"""
        try:
            result = self.visualizer.create_component_distribution_chart(
                self.analyzer.analysis_results, output_filename
            )
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def create_timeline_chart(self, log_data_json, output_filename="timeline_analysis.png"):
        """Create timeline visualization from log data"""
        try:
            import pandas as pd
            log_data = pd.read_json(log_data_json)
            result = self.visualizer.create_timeline_chart(log_data, output_filename)
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def create_error_chart(self, error_patterns_json, output_filename="error_analysis.png"):
        """Create error analysis visualization"""
        try:
            error_patterns = json.loads(error_patterns_json)
            result = self.visualizer.create_error_analysis_chart(
                error_patterns, output_filename
            )
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def create_log_level_chart(self, output_filename="log_level_distribution.png"):
        """Create log level distribution visualization"""
        try:
            result = self.visualizer.create_log_level_distribution(
                self.analyzer.analysis_results, output_filename
            )
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def detect_anomalies(self, log_data_json):
        """Detect anomalies in log data"""
        try:
            import pandas as pd
            log_data = pd.read_json(log_data_json)
            result = self.ml_analyzer.detect_anomalies(log_data)
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def cluster_messages(self, log_data_json, n_clusters=5):
        """Cluster log messages"""
        try:
            import pandas as pd
            log_data = pd.read_json(log_data_json)
            result = self.ml_analyzer.cluster_log_messages(log_data, n_clusters)
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def analyze_error_patterns_ml(self, log_data_json):
        """Analyze error patterns using ML"""
        try:
            import pandas as pd
            log_data = pd.read_json(log_data_json)
            result = self.ml_analyzer.analyze_error_patterns(log_data)
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def predict_error_likelihood(self, log_data_json):
        """Predict error likelihood"""
        try:
            import pandas as pd
            log_data = pd.read_json(log_data_json)
            result = self.ml_analyzer.predict_error_likelihood(log_data)
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

# Global bridge instance
_bridge_instance = None

def get_bridge():
    """Get or create the global bridge instance"""
    global _bridge_instance
    if _bridge_instance is None:
        _bridge_instance = QmdlBridge()
    return _bridge_instance

# Convenience functions for Java calls
def analyze_file(file_path):
    """Convenience function for Java to analyze a single file"""
    return get_bridge().analyze_log_file(file_path)

def analyze_directory(directory_path):
    """Convenience function for Java to analyze a directory"""
    return get_bridge().analyze_log_directory(directory_path)

def generate_report(output_path=None):
    """Convenience function for Java to generate report"""
    return get_bridge().generate_analysis_report(output_path)

def extract_errors():
    """Convenience function for Java to extract error patterns"""
    return get_bridge().extract_error_patterns()

def create_visualizations():
    """Convenience function for Java to create all visualizations"""
    results = {}

    # Create component distribution chart
    results['component_chart'] = json.loads(get_bridge().create_component_chart())

    # Create log level distribution chart
    results['log_level_chart'] = json.loads(get_bridge().create_log_level_chart())

    return json.dumps(results)

def run_ml_analysis(log_data_json):
    """Convenience function for Java to run ML analysis"""
    results = {}

    # Detect anomalies
    results['anomalies'] = json.loads(get_bridge().detect_anomalies(log_data_json))

    # Cluster messages
    results['clusters'] = json.loads(get_bridge().cluster_messages(log_data_json))

    # Analyze error patterns
    results['error_patterns'] = json.loads(get_bridge().analyze_error_patterns_ml(log_data_json))

    # Predict error likelihood
    results['error_prediction'] = json.loads(get_bridge().predict_error_likelihood(log_data_json))

    return json.dumps(results)
