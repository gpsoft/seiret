Param($hwnd)

if ( $hwnd ) {
	$win32 = &{
		$cscode = @"
			[DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Auto)]
			public static extern int GetClassName(IntPtr hWnd, string lpClassName, int nMaxCount);
"@
			return (add-type -memberDefinition $cscode -name "SeiretWin32" -passthru)
	}
	[String]$buf = (0..1000)
		$len = $buf.Length
			$ret = $win32::GetClassName($hwnd, $buf, $len)
			echo $buf.Substring(0, $ret)
} else {
	Get-Process | Where-Object {$_.MainWindowHandle -ne 0} | Select-Object Name, MainWindowTitle,  MainWindowHandle
}
