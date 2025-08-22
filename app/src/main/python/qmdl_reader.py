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
    
    def _extract_hdlc_packets(self, buf, previous_buffer):
        """
        Extract HDLC packets from buffer more carefully, handling escape sequences
        
        Returns:
            dict: {'complete_packets': list, 'remaining_buffer': bytes}
        """
        try:
            complete_packets = []
            remaining_buffer = b''
            
            # Find all 0x7e positions (HDLC frame delimiters)
            frame_starts = []
            i = 0
            while i < len(buf):
                if buf[i] == 0x7e:
                    frame_starts.append(i)
                i += 1
            
            if len(frame_starts) < 2:
                # Not enough frame delimiters, keep everything for next iteration
                return {'complete_packets': [], 'remaining_buffer': buf}
            
            # Extract packets between frame delimiters
            for i in range(len(frame_starts) - 1):
                start = frame_starts[i] + 1  # Skip the 0x7e delimiter
                end = frame_starts[i + 1]    # Up to next 0x7e
                
                if start < end:  # Valid packet bounds
                    packet = buf[start:end]
                    # Only process packets that are at least 3 bytes (minimum for DIAG packet)
                    # and not too large (max reasonable QMDL packet size)
                    if 3 <= len(packet) <= 8192:
                        complete_packets.append(packet)
            
            # Keep everything after the last complete frame
            if frame_starts:
                remaining_buffer = buf[frame_starts[-1]:]
            else:
                remaining_buffer = buf
                
            return {'complete_packets': complete_packets, 'remaining_buffer': remaining_buffer}
            
        except Exception as e:
            print(f"Error in HDLC packet extraction: {e}")
            # Fallback to simple split method
            packets = buf.split(b'\x7e')
            packet_buffer = packets.pop() if packets else b''
            valid_packets = [p for p in packets if 3 <= len(p) <= 8192]
            return {'complete_packets': valid_packets, 'remaining_buffer': packet_buffer}

    def read_qmdl_file(self, file_path, min_size_mb=20):
        """
        Read a QMDL file using scat FileIO approach and display hex data only
        
        Args:
            file_path (str): Path to the QMDL file
            min_size_mb (int): Minimum file size in MB (default: 20MB)
            
        Returns:
            dict: Hex data and metadata, or None if file too small
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
            # Use FileIO from scat project
            io_device = FileIO([file_path])
            return self._read_hex_data(io_device)
            
        except Exception as e:
            print(f"Error reading QMDL file {file_path}: {e}")
            return None
    
    def _read_hex_data(self, io_device):
        """
        Read QMDL file using scat's FileIO approach and parse DIAG packets
        """
        total_bytes_read = 0
        chunk_count = 0
        parsed_packets = []
        hex_samples = []
        
        # Buffer for incomplete packets
        packet_buffer = b''
        
        print("=== QMDL FILE PARSING STARTED ===")
        print(f"Reading from: {io_device.fname}")
        
        try:
            while True:
                # Read 4KB chunks like scat does
                buf = io_device.read(0x1000)
                if len(buf) == 0:
                    break
                    
                chunk_count += 1
                total_bytes_read += len(buf)
                
                # Combine with previous buffer
                buf = packet_buffer + buf
                
                # Process HDLC frames more carefully
                packets = self._extract_hdlc_packets(buf, packet_buffer)
                packet_buffer = packets['remaining_buffer']
                
                # Process complete packets
                for packet in packets['complete_packets']:
                    if len(packet) == 0:
                        continue
                        
                    # Extract signaling data from the packet
                    # The packet is still HDLC encoded and has CRC
                    extracted = self.analyzer.extract_signaling_from_packet(packet, hdlc_encoded=True, has_crc=True)
                    if extracted:
                        parsed_packets.append(extracted)
                        
                        # Log extracted signaling data
                        if len(parsed_packets) <= 20:  # Show more samples
                            print(f"\n=== Signaling Data #{len(parsed_packets)} ===")
                            print(f"Technology: {extracted.get('technology', 'Unknown')}")
                            print(f"Type: {extracted['type']}")
                            if 'timestamp' in extracted:
                                print(f"Timestamp: {extracted['timestamp']}")
                            if 'data' in extracted:
                                print(f"Message Type: {extracted['data'].get('message_type', 'Unknown')}")
                                print(f"Band: {extracted['data'].get('band', 'Unknown')}")
                            if 'radio_id' in extracted:
                                print(f"Radio ID: {extracted['radio_id']}")
                
                # Store hex sample from first few chunks
                if chunk_count <= 5:
                    hex_data = ' '.join(f'{b:02x}' for b in buf[:64])
                    hex_samples.append({
                        'chunk': chunk_count,
                        'hex_data': hex_data,
                        'size': len(buf)
                    })
                
                # Print progress every 100 chunks
                if chunk_count % 100 == 0:
                    stats = self.analyzer.get_extraction_statistics()
                    cellular_state = self.analyzer.get_current_cellular_state()
                    print(f"\nProgress: {chunk_count} chunks, {total_bytes_read} bytes")
                    print(f"Signaling Parsed: {stats['parsed_packets']}/{stats['total_packets']} packets")
                    print(f"Extracted: GSM {stats['gsm_data_extracted']}, UMTS {stats['umts_data_extracted']}, LTE {stats['lte_data_extracted']}, NR {stats['nr_data_extracted']}")
                    print(f"Messages: {stats['system_messages']}, Events: {stats['events_extracted']}")
                    
                    # Display current cellular state
                    if 'lte' in cellular_state:
                        lte = cellular_state['lte']
                        print(f"Current LTE: {lte['cell_id']}, EARFCN {lte['earfcn']} ({lte['band']})")
                    if 'gsm' in cellular_state:
                        gsm = cellular_state['gsm']
                        print(f"Current GSM: {gsm['cell_id']}, ARFCN {gsm['arfcn']} ({gsm['band']})")
                    if 'umts' in cellular_state:
                        umts = cellular_state['umts']
                        print(f"Current UMTS: {umts['cell_id']}, UARFCN {umts['uarfcn']} ({umts['band']})")

        except Exception as e:
            print(f"Error during parsing: {e}")
            import traceback
            traceback.print_exc()
        finally:
            # Close the file
            io_device.close()
            
        # Get final statistics
        stats = self.analyzer.get_extraction_statistics()
        
        # Print final summary
        print(f"\n=== QMDL SIGNALING DATA EXTRACTION COMPLETE ===")
        print(f"Total chunks read: {chunk_count}")
        print(f"Total bytes read: {total_bytes_read}")
        print(f"Average chunk size: {total_bytes_read / chunk_count if chunk_count > 0 else 0:.1f} bytes")
        print(f"\n=== SIGNALING DATA EXTRACTION STATISTICS ===")
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
        print(f"\n=== CURRENT CELLULAR STATE ===")
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
        print(f"\n=== DISPLAYING COMPREHENSIVE SIGNALING INFORMATION ===")
        self.analyzer.display_comprehensive_signaling_info()
        
        # Prepare return data
        return_data = {
            'metadata': {
                'file_path': io_device.fname,
                'total_bytes': total_bytes_read,
                'total_chunks': chunk_count,
                'avg_chunk_size': total_bytes_read / chunk_count if chunk_count > 0 else 0,
                'hex_samples': hex_samples,
                'signaling_extraction_stats': stats,
                'cellular_state': cellular_state,
                'sample_data': parsed_packets[:10]  # First 10 extracted data points
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