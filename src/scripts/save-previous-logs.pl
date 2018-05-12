use strict;
use warnings FATAL => 'all';

$|=1;
die "Usage: save-previous-logs.pl <directory> <log-file-name> <number-of-latest-logs-to-keep>"
    unless @ARGV == 3;
my($logs_dir, $log_file_name, $num_to_keep) = @ARGV;

# remove any files older than the last $num_to_keep files in the $logs_dir directory
my $cmd = "ls -t " . $logs_dir . "/*" . $log_file_name . "*  | sed -e '1," . $num_to_keep . "d' | tr '\\n' '\\0' | xargs -0 rm";
#ls -t | sed -e '1,10d' | xargs -d '\n' rm
print "cmd = $cmd\n";
`$cmd`;

opendir my $dir, $logs_dir or die "Cannot open logs directory $logs_dir";
my @files = readdir $dir;
closedir $dir;

my $count = 0;
my $rename_reqd = 0;
my $latest_count = 0;
# extract the latest count and check if a rename of the latest log file is required
foreach my $file (@files) {
    print "checking $file\n";
    if ($file =~ m/$log_file_name/) {
        print "found matching log file $file\n";
        $count++;
        # assuming that log files will be named like a.log.1, a.log.2, and the log file being created on each run is a.log,
        # try to extract the largest number that appears as a suffix to the log file name
        if (length($file) > length($log_file_name)) {
            my $c = substr($file, length($log_file_name) + 1);
            if ($c > $latest_count) {
                $latest_count = $c;
            }
        }
    }
    if ($file eq $log_file_name) {
        $rename_reqd = 1;
    }
}

#rename the latest log file to the log file with the greatest suffix
if ($rename_reqd == 1) {
    rename $logs_dir . "/" . $log_file_name, $logs_dir . "/" . $log_file_name . "." . ($latest_count + 1);
}