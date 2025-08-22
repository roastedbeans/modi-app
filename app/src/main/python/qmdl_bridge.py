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
        Recursively convert datetime objects and bytes to strings for JSON serialization
        """
        if isinstance(obj, datetime.datetime):
            return obj.isoformat()
        elif isinstance(obj, bytes):
            return obj.hex()  # Convert bytes to hex string
        elif isinstance(obj, dict):
            return {key: self._convert_datetime_to_string(value) for key, value in obj.items()}
        elif isinstance(obj, list):
            return [self._convert_datetime_to_string(item) for item in obj]
        elif isinstance(obj, (int, float, str, bool, type(None))):
            return obj  # These types are already JSON serializable
        else:
            return str(obj)  # Convert any other type to string





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


def read_qmdl_file(file_path):
    """Convenience function for Java to read QMDL file"""
    return get_bridge().read_qmdl_file(file_path)

def process_qmdl_files_from_java(files_json):
    """Convenience function for Java to process QMDL files list"""
    return get_bridge().process_qmdl_files_from_java(files_json)