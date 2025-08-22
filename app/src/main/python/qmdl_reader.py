"""
QMDL File Reader - Simplified Hex-Only Version
Python module for reading QMDL files and displaying hex data
Based on scat project FileIO approach
"""

import os
import datetime
from pathlib import Path
from fileio import FileIO
from signaling import SignalingAnalyzer


class QmdlReader:
    """Class for reading QMDL files and parsing DIAG packets"""

    def __init__(self):
        self.analyzer = SignalingAnalyzer()
    
    # Removed _extract_hdlc_packets - now using SCAT's proper implementation
    # SCAT's QualcommParser.run_diag() handles HDLC extraction correctly

    def read_qmdl_file(self, file_path, min_size_mb=20):
        """
        Read a QMDL file using SCAT's correct approach with QualcommParser.run_diag()

        Args:
            file_path (str): Path to the QMDL file
            min_size_mb (int): Minimum file size in MB (default: 20MB)

        Returns:
            dict: Parsed signaling data and metadata, or None if file too small
        """
        # Check file size first
        try:
            file_size = os.path.getsize(file_path)
            min_size_bytes = min_size_mb * 1024 * 1024

            if file_size < min_size_bytes:
                print(f"QMDL file too small: {file_path} ({file_size / (1024*1024):.1f}MB < {min_size_mb}MB)")
                return None

            print(f"Reading QMDL file: {file_path} ({file_size / (1024*1024):.1f}MB)")

        except Exception as e:
            print(f"Error accessing file {file_path}: {e}")
            return None

        try:
            # Use SCAT's correct approach: FileIO + QualcommParser.run_diag()
            io_device = FileIO([file_path])

            # Set up QualcommParser with FileIO device
            parser = self.analyzer.parser
            parser.set_io_device(io_device)

            # Use SCAT's run_diag method which handles HDLC properly
            return self._read_with_scat_parser(parser, file_path)

        except Exception as e:
            print(f"Error reading QMDL file {file_path}: {e}")
            import traceback
            traceback.print_exc()
            return None
    
    def _read_with_scat_parser(self, parser, file_path):
        """
        Read QMDL file using SCAT's correct QualcommParser.run_diag() approach
        This follows SCAT's proper packet processing implementation
        """
        print("=== QMDL FILE PARSING STARTED (SCAT METHOD) ===")
        print(f"Reading from: {file_path}")

        # Reset analyzer statistics before parsing
        self.analyzer.reset_statistics()

        # Set up SCAT integration for proper packet processing
        self.analyzer.setup_scat_integration()

        try:
            # Use SCAT's run_diag method - this handles HDLC properly!
            parser.run_diag()

            # Get final statistics
            stats = self.analyzer.get_extraction_statistics()
            cellular_state = self.analyzer.get_current_cellular_state()

            # Print final summary
            print("\n=== QMDL SIGNALING DATA EXTRACTION COMPLETE ===")
            print(f"Total packets processed: {stats['total_packets']}")
            print(f"Successfully parsed: {stats['parsed_packets']}")
            print(f"GSM signaling extracted: {stats['gsm_data_extracted']}")
            print(f"UMTS signaling extracted: {stats['umts_data_extracted']}")
            print(f"LTE signaling extracted: {stats['lte_data_extracted']}")
            print(f"NR (5G) signaling extracted: {stats['nr_data_extracted']}")
            print(f"System messages: {stats['system_messages']}")
            print(f"Events extracted: {stats['events_extracted']}")

            # Display current cellular state
            cellular_state = self.analyzer.get_current_cellular_state()
            print("\n=== CURRENT CELLULAR STATE ===")
            if 'lte' in cellular_state:
                lte = cellular_state['lte']
                print(f"LTE: {lte['cell_id']}, EARFCN {lte['earfcn']} ({lte['band']})")
            if 'gsm' in cellular_state:
                gsm = cellular_state['gsm']
                print(f"GSM: {gsm['cell_id']}, ARFCN {gsm['arfcn']} ({gsm['band']})")
            if 'umts' in cellular_state:
                umts = cellular_state['umts']
                print(f"UMTS: {umts['cell_id']}, UARFCN {umts['uarfcn']} ({umts['band']})")

            # Display comprehensive signaling information
            print("\n=== DISPLAYING COMPREHENSIVE SIGNALING INFORMATION ===")
            self.analyzer.display_comprehensive_signaling_info()

            # Prepare return data in the same format as before
            return_data = {
                'metadata': {
                    'file_path': file_path,
                    'total_bytes': 0,  # We don't track this in SCAT method
                    'total_chunks': 0,
                    'avg_chunk_size': 0,
                    'hex_samples': [],  # SCAT handles this internally
                    'signaling_extraction_stats': stats,
                    'cellular_state': cellular_state,
                    'sample_data': []  # SCAT processes packets internally
                },
                'status': 'completed',
                'message': f'Signaling analyzer successfully parsed {stats["parsed_packets"]}/{stats["total_packets"]} packets (GSM: {stats["gsm_data_extracted"]}, UMTS: {stats["umts_data_extracted"]}, LTE: {stats["lte_data_extracted"]}, Messages: {stats["system_messages"]})'
            }

            # Add all extracted signaling data
            all_data = self.analyzer.get_all_extracted_data()
            return_data['all_extracted_data'] = all_data

            # Add card-formatted data for UI
            card_data = self.analyzer.get_card_data()
            return_data['card_data'] = card_data

            # Add PCAP-like statistics for detailed analysis
            pcap_stats = self.analyzer.get_pcap_statistics()
            return_data['pcap_statistics'] = pcap_stats

            return return_data

        except Exception as e:
            print(f"Error during SCAT parsing: {e}")
            import traceback
            traceback.print_exc()
            return None
    
    def list_qmdl_files(self, directory, min_size_mb=20):
        """
        List QMDL files in a directory that are >= specified size
        
        Args:
            directory (str): Directory path
            min_size_mb (int): Minimum file size in MB (default: 20MB)
            
        Returns:
            list: List of QMDL file paths that meet size criteria
        """
        qmdl_files = []
        min_size_bytes = min_size_mb * 1024 * 1024  # Convert MB to bytes
        
        try:
            if os.path.exists(directory) and os.access(directory, os.R_OK):
                for file in os.listdir(directory):
                    if file.endswith('.qmdl') or file.endswith('.QMDL'):
                        file_path = os.path.join(directory, file)
                        try:
                            file_size = os.path.getsize(file_path)
                            if file_size >= min_size_bytes:
                                qmdl_files.append(file_path)
                                print(f"Found QMDL file >= {min_size_mb}MB: {file} ({file_size / (1024*1024):.1f}MB)")
                            else:
                                print(f"Skipping QMDL file < {min_size_mb}MB: {file} ({file_size / (1024*1024):.1f}MB)")
                        except Exception as e:
                            print(f"Could not get size for {file}: {e}")
            else:
                print(f"Directory not accessible: {directory}")
                
        except Exception as e:
            print(f"Error listing QMDL files in {directory}: {e}")
        
        return qmdl_files

    def get_file_info(self, file_path):
        """
        Get information about a QMDL file
        
        Args:
            file_path (str): Path to the QMDL file
            
        Returns:
            dict: File information or None
        """
        try:
            stat = os.stat(file_path)
            return {
                'path': file_path,
                'size': stat.st_size,
                'modified': datetime.datetime.fromtimestamp(stat.st_mtime),
                'created': datetime.datetime.fromtimestamp(stat.st_ctime)
            }
        except Exception as e:
            print(f"Error getting file info for {file_path}: {e}")
            return None