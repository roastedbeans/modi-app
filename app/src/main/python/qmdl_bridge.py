"""
QMDL Python-Java Bridge
Interface module between Java application and Python QMDL reader
"""

import json
import os
import datetime
from qmdl_reader import QmdlReader

class QmdlBridge:
    """Bridge class for Java-Python communication"""

    def __init__(self):
        self.qmdl_reader = QmdlReader()
        
    def _convert_datetime_to_string(self, obj):
        """
        Recursively convert datetime objects to strings for JSON serialization
        """
        if isinstance(obj, datetime.datetime):
            return obj.isoformat()
        elif isinstance(obj, dict):
            return {key: self._convert_datetime_to_string(value) for key, value in obj.items()}
        elif isinstance(obj, list):
            return [self._convert_datetime_to_string(item) for item in obj]
        else:
            return obj

    def analyze_log_file(self, file_path):
        """Read and analyze a single QMDL file"""
        try:
            result = self.qmdl_reader.read_qmdl_file(file_path)
            if result:
                # Convert datetime objects to strings for JSON serialization
                result_serializable = self._convert_datetime_to_string(result)
                return json.dumps(result_serializable)
            else:
                return json.dumps({'error': 'No result returned'})
        except Exception as e:
            return json.dumps({'error': str(e)})

    def analyze_log_directory(self, directory_path):
        """List QMDL files in a directory"""
        try:
            qmdl_files = self.qmdl_reader.list_qmdl_files(directory_path)
            result = {
                'directory': directory_path,
                'qmdl_files_found': len(qmdl_files),
                'files': []
            }
            
            for file_path in qmdl_files:
                file_info = self.qmdl_reader.get_file_info(file_path)
                if file_info:
                    result['files'].append({
                        'path': file_info['path'],
                        'size': file_info['size'],
                        'modified': file_info['modified'].isoformat(),
                        'created': file_info['created'].isoformat()
                    })
            
            # Convert any remaining datetime objects to strings
            result_serializable = self._convert_datetime_to_string(result)
            return json.dumps(result_serializable)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def generate_analysis_report(self, output_path=None):
        """Generate a simple QMDL file listing report"""
        try:
            # For now, just return a placeholder since we're only reading files
            result = {
                'report_type': 'QMDL File Listing',
                'message': 'QMDL reader is ready for file analysis',
                'output_path': output_path
            }
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def extract_error_patterns(self):
        """Extract basic error patterns from QMDL files"""
        try:
            # For now, return a placeholder since we're only reading files
            result = {
                'message': 'QMDL reader is ready for error pattern extraction',
                'patterns_found': 0
            }
            return json.dumps(result)
        except Exception as e:
            return json.dumps({'error': str(e)})

    def test_qmdl_file_access(self, directory_path):
        """Test accessibility of QMDL files in a directory"""
        try:
            qmdl_files = self.qmdl_reader.list_qmdl_files(directory_path)
            result = {
                'directory': directory_path,
                'qmdl_files_found': len(qmdl_files),
                'files': [],
                'debug_info': {
                    'python_can_access_dir': os.path.exists(directory_path),
                    'python_can_read_dir': os.access(directory_path, os.R_OK) if os.path.exists(directory_path) else False
                }
            }
            
            for file_path in qmdl_files:
                file_info = self.qmdl_reader.get_file_info(file_path)
                if file_info:
                    result['files'].append({
                        'path': file_info['path'],
                        'size': file_info['size'],
                        'modified': file_info['modified'].isoformat(),
                        'created': file_info['created'].isoformat()
                    })
            
            # Convert any remaining datetime objects to strings
            result_serializable = self._convert_datetime_to_string(result)
            return json.dumps(result_serializable)
        except Exception as e:
            return json.dumps({'error': str(e), 'debug_info': {
                'python_can_access_dir': os.path.exists(directory_path),
                'python_can_read_dir': os.access(directory_path, os.R_OK) if os.path.exists(directory_path) else False
            }})

    def read_qmdl_file(self, file_path):
        """Read a QMDL file and return parsed DIAG data"""
        try:
            result = self.qmdl_reader.read_qmdl_file(file_path)
            if result:
                # Convert datetime objects to strings for JSON serialization
                result_serializable = self._convert_datetime_to_string(result)
                return json.dumps(result_serializable)
            else:
                return json.dumps({'error': 'File too small or could not be read'})
        except Exception as e:
            return json.dumps({'error': str(e)})
    
    def process_qmdl_files_from_java(self, files_json):
        """Process QMDL files provided by Java (since Java has root access)"""
        try:
            files_data = json.loads(files_json)
            result = {
                'directory': files_data.get('directory', 'unknown'),
                'qmdl_files_found': len(files_data.get('files', [])),
                'files': [],
                'source': 'java_with_root_access'
            }
            
            for file_info in files_data.get('files', []):
                # Java already filtered for 20MB+ files
                result['files'].append({
                    'path': file_info['path'],
                    'size': file_info['size'],
                    'modified': file_info.get('modified', 'unknown'),
                    'created': file_info.get('created', 'unknown')
                })
            
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

def test_qmdl_file_access(directory_path):
    """Convenience function for Java to test QMDL file access"""
    return get_bridge().test_qmdl_file_access(directory_path)

def read_qmdl_file(file_path):
    """Convenience function for Java to read QMDL file"""
    return get_bridge().read_qmdl_file(file_path)

def process_qmdl_files_from_java(files_json):
    """Convenience function for Java to process QMDL files list"""
    return get_bridge().process_qmdl_files_from_java(files_json)

def create_visualizations():
    """Convenience function for Java to create visualizations (placeholder)"""
    result = {
        'message': 'Visualizations not available in simplified QMDL reader',
        'status': 'not_implemented'
    }
    return json.dumps(result)

def run_ml_analysis(log_data_json):
    """Convenience function for Java to run ML analysis (placeholder)"""
    result = {
        'message': 'ML analysis not available in simplified QMDL reader',
        'status': 'not_implemented'
    }
    return json.dumps(result)