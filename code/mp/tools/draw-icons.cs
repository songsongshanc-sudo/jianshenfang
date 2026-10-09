using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

public static class GymIcons
{
    public static void Build(string root)
    {
        Directory.CreateDirectory(Path.Combine(root, "tab"));
        Directory.CreateDirectory(Path.Combine(root, "icon"));
        var mute = Color.FromArgb(168, 162, 158);
        var on = Color.FromArgb(194, 65, 12);
        var ink = Color.FromArgb(28, 25, 23);
        Save(Path.Combine(root, "tab", "home.png"), 81, mute, Home);
        Save(Path.Combine(root, "tab", "home-on.png"), 81, on, Home);
        Save(Path.Combine(root, "tab", "coach.png"), 81, mute, Dumbbell);
        Save(Path.Combine(root, "tab", "coach-on.png"), 81, on, Dumbbell);
        Save(Path.Combine(root, "tab", "join.png"), 81, mute, Shop);
        Save(Path.Combine(root, "tab", "join-on.png"), 81, on, Shop);
        Save(Path.Combine(root, "tab", "mine.png"), 81, mute, User);
        Save(Path.Combine(root, "tab", "mine-on.png"), 81, on, User);
        string[] names = { "pin", "info", "route", "phone", "card", "order", "lesson", "ticket", "wrench", "chat", "box", "dumbbell", "star", "bell", "cap", "sliders", "wifi", "camera" };
        Action<Graphics, Pen, float>[] draws = { Pin, Info, Route, Phone, Card, Order, Lesson, Ticket, Wrench, Chat, Box, DumbbellUnit, Star, Bell, Cap, Sliders, Wifi, Camera };
        for (int i = 0; i < names.Length; i++)
        {
            int n = i;
            Save(Path.Combine(root, "icon", names[i] + ".png"), 96, ink, (g, c, s) =>
            {
                using (var pen = MakePen(c, s)) draws[n](g, pen, s);
            });
        }
    }

    static void Save(string path, int size, Color color, Action<Graphics, Color, float> draw)
    {
        using (var bmp = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (var g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.Clear(Color.Transparent);
            draw(g, color, size / 24f);
            bmp.Save(path, ImageFormat.Png);
        }
    }

    static Pen MakePen(Color c, float u)
    {
        var pen = new Pen(c, Math.Max(1.8f, u * 1.55f));
        pen.StartCap = LineCap.Round;
        pen.EndCap = LineCap.Round;
        pen.LineJoin = LineJoin.Round;
        return pen;
    }

    static PointF Pt(float u, float x, float y) { return new PointF(x * u, y * u); }

    static void Round(Graphics g, Pen pen, float u, float x, float y, float w, float h, float r)
    {
        using (var path = new GraphicsPath())
        {
            float d = r * 2f * u;
            float X = x * u, Y = y * u, W = w * u, H = h * u;
            path.AddArc(X, Y, d, d, 180, 90);
            path.AddArc(X + W - d, Y, d, d, 270, 90);
            path.AddArc(X + W - d, Y + H - d, d, d, 0, 90);
            path.AddArc(X, Y + H - d, d, d, 90, 90);
            path.CloseFigure();
            g.DrawPath(pen, path);
        }
    }

    static void Home(Graphics g, Color c, float u)
    {
        using (var pen = MakePen(c, u))
        {
            g.DrawLines(pen, new[] { Pt(u, 4, 11), Pt(u, 12, 4.2f), Pt(u, 20, 11) });
            g.DrawLines(pen, new[] { Pt(u, 6.3f, 10.2f), Pt(u, 6.3f, 19.4f), Pt(u, 17.7f, 19.4f), Pt(u, 17.7f, 10.2f) });
            g.DrawLines(pen, new[] { Pt(u, 10.3f, 19.4f), Pt(u, 10.3f, 14.2f), Pt(u, 13.7f, 14.2f), Pt(u, 13.7f, 19.4f) });
        }
    }

    static void Dumbbell(Graphics g, Color c, float u)
    {
        using (var pen = MakePen(c, u)) DumbbellUnit(g, pen, u);
    }

    static void DumbbellUnit(Graphics g, Pen pen, float u)
    {
        g.DrawLine(pen, Pt(u, 7.2f, 12), Pt(u, 16.8f, 12));
        g.DrawLine(pen, Pt(u, 5.6f, 8.2f), Pt(u, 5.6f, 15.8f));
        g.DrawLine(pen, Pt(u, 3.6f, 9.3f), Pt(u, 3.6f, 14.7f));
        g.DrawLine(pen, Pt(u, 18.4f, 8.2f), Pt(u, 18.4f, 15.8f));
        g.DrawLine(pen, Pt(u, 20.4f, 9.3f), Pt(u, 20.4f, 14.7f));
    }

    static void Shop(Graphics g, Color c, float u)
    {
        using (var pen = MakePen(c, u))
        {
            g.DrawLine(pen, Pt(u, 3.4f, 9.2f), Pt(u, 20.6f, 9.2f));
            g.DrawArc(pen, 3.4f * u, 6.2f * u, 4.3f * u, 3.2f * u, 180, 180);
            g.DrawArc(pen, 7.7f * u, 6.2f * u, 4.3f * u, 3.2f * u, 180, 180);
            g.DrawArc(pen, 12f * u, 6.2f * u, 4.3f * u, 3.2f * u, 180, 180);
            g.DrawArc(pen, 16.3f * u, 6.2f * u, 4.3f * u, 3.2f * u, 180, 180);
            g.DrawRectangle(pen, 5f * u, 11.2f * u, 14f * u, 8.2f * u);
            g.DrawLines(pen, new[] { Pt(u, 10.2f, 19.4f), Pt(u, 10.2f, 14.2f), Pt(u, 13.8f, 14.2f), Pt(u, 13.8f, 19.4f) });
        }
    }

    static void User(Graphics g, Color c, float u)
    {
        using (var pen = MakePen(c, u))
        {
            g.DrawEllipse(pen, 8.6f * u, 3.6f * u, 6.8f * u, 6.8f * u);
            g.DrawArc(pen, 4.4f * u, 12.2f * u, 15.2f * u, 12f * u, 200, 140);
        }
    }

    static void Pin(Graphics g, Pen pen, float u)
    {
        g.DrawEllipse(pen, 7.2f * u, 3.2f * u, 9.6f * u, 9.6f * u);
        g.DrawEllipse(pen, 10.2f * u, 6.2f * u, 3.6f * u, 3.6f * u);
        g.DrawLines(pen, new[] { Pt(u, 7.6f, 11.4f), Pt(u, 12, 20.4f), Pt(u, 16.4f, 11.4f) });
    }

    static void Info(Graphics g, Pen pen, float u)
    {
        g.DrawEllipse(pen, 3.6f * u, 3.6f * u, 16.8f * u, 16.8f * u);
        g.DrawLine(pen, Pt(u, 12, 10.6f), Pt(u, 12, 17.2f));
        g.DrawEllipse(pen, 11.1f * u, 7.1f * u, 1.8f * u, 1.8f * u);
    }

    static void Route(Graphics g, Pen pen, float u)
    {
        g.DrawEllipse(pen, 4f * u, 4f * u, 4.2f * u, 4.2f * u);
        g.DrawBezier(pen, Pt(u, 8.2f, 6.2f), Pt(u, 16, 4), Pt(u, 8, 16), Pt(u, 16.2f, 16.4f));
        g.DrawEllipse(pen, 14.4f * u, 14.6f * u, 4.4f * u, 4.4f * u);
    }

    static void Phone(Graphics g, Pen pen, float u)
    {
        Round(g, pen, u, 7.2f, 2.8f, 9.6f, 18.4f, 1.6f);
        g.DrawLine(pen, Pt(u, 10.2f, 5.4f), Pt(u, 13.8f, 5.4f));
        g.DrawEllipse(pen, 10.7f * u, 17.2f * u, 2.6f * u, 2.6f * u);
    }

    static void Card(Graphics g, Pen pen, float u)
    {
        Round(g, pen, u, 2.8f, 6f, 18.4f, 12f, 1.6f);
        g.DrawLine(pen, Pt(u, 2.8f, 10.2f), Pt(u, 21.2f, 10.2f));
    }

    static void Order(Graphics g, Pen pen, float u)
    {
        Round(g, pen, u, 5f, 3f, 14f, 18f, 1.4f);
        g.DrawLine(pen, Pt(u, 8.2f, 8.2f), Pt(u, 15.8f, 8.2f));
        g.DrawLine(pen, Pt(u, 8.2f, 12f), Pt(u, 15.8f, 12f));
        g.DrawLine(pen, Pt(u, 8.2f, 15.8f), Pt(u, 13.2f, 15.8f));
    }

    static void Lesson(Graphics g, Pen pen, float u)
    {
        g.DrawEllipse(pen, 3.4f * u, 3.4f * u, 17.2f * u, 17.2f * u);
        g.DrawLines(pen, new[] { Pt(u, 10.2f, 8.2f), Pt(u, 16, 12), Pt(u, 10.2f, 15.8f) });
    }

    static void Ticket(Graphics g, Pen pen, float u)
    {
        Round(g, pen, u, 3f, 6f, 18f, 12f, 1.6f);
        pen.DashStyle = DashStyle.Dot;
        g.DrawLine(pen, Pt(u, 12, 7.2f), Pt(u, 12, 16.8f));
        pen.DashStyle = DashStyle.Solid;
    }

    static void Wrench(Graphics g, Pen pen, float u)
    {
        g.DrawArc(pen, 3.2f * u, 3.2f * u, 8f * u, 8f * u, 220, 220);
        g.DrawLine(pen, Pt(u, 9.2f, 9.6f), Pt(u, 19.2f, 19.6f));
        g.DrawLine(pen, Pt(u, 16.2f, 16.6f), Pt(u, 19.6f, 15.2f));
        g.DrawLine(pen, Pt(u, 16.6f, 17.2f), Pt(u, 18.2f, 20.2f));
    }

    static void Chat(Graphics g, Pen pen, float u)
    {
        Round(g, pen, u, 3f, 3.4f, 18f, 12.2f, 2.2f);
        g.DrawLines(pen, new[] { Pt(u, 8, 15.6f), Pt(u, 7.2f, 20.2f), Pt(u, 12.4f, 15.6f) });
    }

    static void Box(Graphics g, Pen pen, float u)
    {
        g.DrawLines(pen, new[] { Pt(u, 12, 3.4f), Pt(u, 20.4f, 7.4f), Pt(u, 12, 11.4f), Pt(u, 3.6f, 7.4f), Pt(u, 12, 3.4f) });
        g.DrawLine(pen, Pt(u, 12, 11.4f), Pt(u, 12, 20.4f));
        g.DrawLine(pen, Pt(u, 3.6f, 7.4f), Pt(u, 3.6f, 16.4f));
        g.DrawLine(pen, Pt(u, 20.4f, 7.4f), Pt(u, 20.4f, 16.4f));
        g.DrawLine(pen, Pt(u, 3.6f, 16.4f), Pt(u, 12, 20.4f));
        g.DrawLine(pen, Pt(u, 20.4f, 16.4f), Pt(u, 12, 20.4f));
    }

    static void Star(Graphics g, Pen pen, float u)
    {
        g.DrawPolygon(pen, new[] {
            Pt(u, 12, 3.2f), Pt(u, 14.5f, 9.1f), Pt(u, 20.8f, 9.6f), Pt(u, 16, 13.6f),
            Pt(u, 17.6f, 19.8f), Pt(u, 12, 16.4f), Pt(u, 6.4f, 19.8f), Pt(u, 8, 13.6f),
            Pt(u, 3.2f, 9.6f), Pt(u, 9.5f, 9.1f)
        });
    }

    static void Bell(Graphics g, Pen pen, float u)
    {
        g.DrawArc(pen, 5.2f * u, 4.2f * u, 13.6f * u, 12.4f * u, 200, 140);
        g.DrawLine(pen, Pt(u, 5.6f, 14.2f), Pt(u, 18.4f, 14.2f));
        g.DrawArc(pen, 9.6f * u, 14.2f * u, 4.8f * u, 4.2f * u, 0, 180);
        g.DrawLine(pen, Pt(u, 12, 3.2f), Pt(u, 12, 4.6f));
    }

    static void Cap(Graphics g, Pen pen, float u)
    {
        g.DrawPolygon(pen, new[] { Pt(u, 12, 4.2f), Pt(u, 20.6f, 8.6f), Pt(u, 12, 13), Pt(u, 3.4f, 8.6f) });
        g.DrawLine(pen, Pt(u, 7.2f, 11.2f), Pt(u, 7.2f, 16.2f));
        g.DrawArc(pen, 7.2f * u, 13.4f * u, 9.6f * u, 5.2f * u, 0, 180);
        g.DrawLine(pen, Pt(u, 18.4f, 8.8f), Pt(u, 18.4f, 15.6f));
    }

    static void Sliders(Graphics g, Pen pen, float u)
    {
        g.DrawLine(pen, Pt(u, 4, 7), Pt(u, 20, 7));
        g.DrawLine(pen, Pt(u, 4, 12), Pt(u, 20, 12));
        g.DrawLine(pen, Pt(u, 4, 17), Pt(u, 20, 17));
        g.DrawEllipse(pen, 12.2f * u, 5.2f * u, 3.6f * u, 3.6f * u);
        g.DrawEllipse(pen, 7.2f * u, 10.2f * u, 3.6f * u, 3.6f * u);
        g.DrawEllipse(pen, 14.2f * u, 15.2f * u, 3.6f * u, 3.6f * u);
    }

    static void Wifi(Graphics g, Pen pen, float u)
    {
        g.DrawArc(pen, 4f * u, 6f * u, 16f * u, 12f * u, 200, 140);
        g.DrawArc(pen, 7f * u, 9.2f * u, 10f * u, 8.4f * u, 200, 140);
        g.DrawEllipse(pen, 10.6f * u, 15.6f * u, 2.8f * u, 2.8f * u);
    }

    static void Camera(Graphics g, Pen pen, float u)
    {
        Round(g, pen, u, 3f, 7f, 18f, 12.4f, 1.8f);
        g.DrawArc(pen, 8.2f * u, 3.6f * u, 7.6f * u, 4.4f * u, 200, 140);
        g.DrawEllipse(pen, 8.4f * u, 9.6f * u, 7.2f * u, 7.2f * u);
    }
}
