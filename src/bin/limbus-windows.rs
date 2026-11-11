#[cfg(windows)]
mod app {
    use std::{fs, io, path::Path, thread, time::{Duration, Instant}};
    use enigo::{Button, Coordinate, Direction, Enigo, Mouse, Settings};
    use image::{imageops, RgbaImage};
    use screenshots::Screen;
    use limbus_core::similarity_argb;

    #[link(name = "user32")]
    extern "system" {
        fn GetForegroundWindow() -> isize;
        fn GetWindowTextW(hwnd: isize, s: *mut u16, max: i32) -> i32;
    }
    const W: u32 = 120;
    const H: u32 = 60;
    const MAX_ACTIONS: usize = 60;

    struct Target { x: i32, y: i32, pixels: Vec<u32> }
    fn foreground_is_game() -> bool {
        let mut chars = [0u16; 256];
        let count = unsafe { GetWindowTextW(GetForegroundWindow(), chars.as_mut_ptr(), 256) };
        count > 0 && String::from_utf16_lossy(&chars[..count as usize]).to_lowercase().contains("limbus")
    }
    fn screen() -> Result<(RgbaImage, i32, i32), String> {
        let all = Screen::all().map_err(|e| e.to_string())?;
        let primary = all.first().ok_or("No display")?;
        let image = primary.capture().map_err(|e| e.to_string())?;
        Ok((image, primary.display_info.x, primary.display_info.y))
    }
    fn patch(img: &RgbaImage, x: i32, y: i32) -> Option<RgbaImage> {
        let (l, t) = (x - W as i32 / 2, y - H as i32 / 2);
        if l < 0 || t < 0 || l + W as i32 > img.width() as i32 || t + H as i32 > img.height() as i32 {
            return None;
        }
        Some(imageops::crop_imm(img, l as u32, t as u32, W, H).to_image())
    }
    fn rgb_u32(image: &RgbaImage) -> Vec<u32> {
        image.pixels().map(|p| {
            let c = p.0;
            0xff000000 | ((c[0] as u32) << 16) | ((c[1] as u32) << 8) | c[2] as u32
        }).collect()
    }
    fn config_path() -> &'static str { "config.tsv" }
    fn capture(label: &str, x: i32, y: i32) -> Result<(), String> {
        if !["win", "fire", "finish"].contains(&label) { return Err("Unknown label".into()); }
        if !foreground_is_game() { return Err("Focus the Limbus game window before capturing".into()); }
        let (image, ox, oy) = screen()?;
        // Capture arguments use monitor-local pixels.
        let piece = patch(&image, x, y).ok_or("Point is too near the screen edge")?;
        fs::create_dir_all("templates").map_err(|e| e.to_string())?;
        piece.save(format!("templates/{label}.png")).map_err(|e| e.to_string())?;
        let old = fs::read_to_string(config_path()).unwrap_or_default();
        let mut lines: Vec<String> = old.lines().filter(|l| !l.starts_with(&format!("{label}\t"))).map(str::to_owned).collect();
        lines.push(format!("{label}\t{x}\t{y}"));
        fs::write(config_path(), lines.join("\n") + "\n").map_err(|e| e.to_string())?;
        println!("Saved {label} at monitor-local ({x},{y}); monitor origin ({ox},{oy})");
        Ok(())
    }
    fn load(label: &str) -> Result<Option<Target>, String> {
        let config = fs::read_to_string(config_path()).map_err(|e| e.to_string())?;
        let Some(line) = config.lines().find(|l| l.starts_with(&format!("{label}\t"))) else {
            return Ok(None);
        };
        let parts: Vec<&str> = line.split('\t').collect();
        if parts.len() != 3 { return Err(format!("Invalid config for {label}")); }
        let x = parts[1].parse().map_err(|_| format!("Invalid X for {label}"))?;
        let y = parts[2].parse().map_err(|_| format!("Invalid Y for {label}"))?;
        let path = format!("templates/{label}.png");
        if !Path::new(&path).exists() { return Err(format!("Missing {path}")); }
        let image = image::open(&path).map_err(|e| e.to_string())?.into_rgba8();
        if image.dimensions() != (W,H) { return Err("Template dimensions mismatch".into()); }
        Ok(Some(Target{x,y,pixels: rgb_u32(&image)}))
    }
    fn score(img: &RgbaImage, target: &Target) -> f32 {
        match patch(img,target.x,target.y) {
            Some(piece) => similarity_argb(&rgb_u32(&piece),&target.pixels),
            None => 0.0
        }
    }
    fn run() -> Result<(), String> {
        let win=load("win")?.ok_or("Capture win first")?;
        let fire=load("fire")?.ok_or("Capture fire first")?;
        let finish=load("finish")?;
        let mut enigo=Enigo::new(&Settings::default()).map_err(|e| e.to_string())?;
        let started=Instant::now();
        let mut actions=0usize;
        let mut failures=0usize;
        let mut confirm=0usize;
        let mut clear_confirm=0usize;
        let mut phase_fire=false;
        println!("Experimental normal combat ONLY. Ctrl+C to stop.");
        loop {
            if started.elapsed()>Duration::from_secs(20*60) || actions>=MAX_ACTIONS {
                return Err("Safety limit reached: stopped".into());
            }
            if !foreground_is_game() { return Err("Game no longer foreground: stopped".into()); }
            let (frame, ox, oy)=screen()?;
            if let Some(ref done)=finish {
                if score(&frame,done)>0.95 { clear_confirm+=1; } else { clear_confirm=0; }
                if clear_confirm>=2 { println!("Clear screen confirmed. Stopped."); return Ok(()); }
            }
            let target=if phase_fire {&fire} else {&win};
            let confidence=score(&frame,target);
            if confidence<0.945 {
                confirm=0; failures+=1;
                if failures>=30 { return Err("Template not recognized: stopped".into()); }
                thread::sleep(Duration::from_secs(2));
                continue;
            }
            failures=0; confirm+=1;
            if confirm<2 { thread::sleep(Duration::from_millis(850)); continue; }
            confirm=0;
            if !foreground_is_game() { return Err("Focus changed before tap".into()); }
            enigo.move_mouse(target.x+ox,target.y+oy,Coordinate::Abs).map_err(|e| e.to_string())?;
            enigo.button(Button::Left, Direction::Click).map_err(|e| e.to_string())?;
            actions+=1;
            println!("{} {:.1}% ({actions}/{MAX_ACTIONS})",if phase_fire {"Fire"} else {"Win"},confidence*100.0);
            phase_fire = !phase_fire;
            thread::sleep(Duration::from_secs(if phase_fire { 2 } else { 6 }));
        }
    }
    pub fn main() {
        let args: Vec<String>=std::env::args().collect();
        let result=match args.get(1).map(String::as_str) {
            Some("capture") if args.len()==5 => {
                match (args[3].parse::<i32>(),args[4].parse::<i32>()) {
                    (Ok(x),Ok(y))=> capture(&args[2],x,y),
                    _=> Err("Coordinates must be integers".into()),
                }
            }
            Some("run")=>run(),
            _ => Err("Usage: limbus-windows capture win|fire|finish X Y  |  limbus-windows run".into()),
        };
        if let Err(e)=result { eprintln!("{e}"); std::process::exit(1); }
    }
}
#[cfg(windows)]
fn main() { app::main(); }
#[cfg(not(windows))]
fn main() { println!("Windows executable: build on Windows."); }
